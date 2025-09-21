package ai.docsite.translator.git;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.diff.DiffMetadata;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates cloning, fetching, commit selection, merge, and diff preparation for translation branches.
 */
public class GitWorkflowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitWorkflowService.class);
    private static final String UPSTREAM_MAIN_BRANCH = "main";

    private final Path workspaceRoot;
    private final DiffAnalyzer diffAnalyzer;

    public GitWorkflowService(Path workspaceRoot, DiffAnalyzer diffAnalyzer) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.diffAnalyzer = Objects.requireNonNull(diffAnalyzer, "diffAnalyzer");
    }

    public GitWorkflowService() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "ai-docsite-translator"), new DiffAnalyzer());
    }

    public GitWorkflowResult prepareSyncBranch(Config config) {
        Objects.requireNonNull(config, "config");
        try {
            Files.createDirectories(workspaceRoot);
            Path upstreamDir = cloneFresh(config.upstreamUrl(), "upstream");
            Path originDir = cloneFresh(config.originUrl(), "origin");

            try (Git origin = Git.open(originDir.toFile())) {
                ensureBaseBranchCheckedOut(origin, config.originBranch());
                configureUpstreamRemote(origin, upstreamDir.toUri());

                ObjectId upstreamHead = fetchUpstreamHead(origin, UPSTREAM_MAIN_BRANCH);
                ObjectId originHead = resolveRequired(origin.getRepository(), "refs/heads/" + config.originBranch());

                List<RevCommit> pendingCommits = findPendingUpstreamCommits(origin.getRepository(), upstreamHead, originHead);
                if (pendingCommits.isEmpty()) {
                    LOGGER.info("No untranslated commits detected");
                    return GitWorkflowResult.empty(upstreamDir, originDir);
                }

                RevCommit targetCommit = selectTargetCommit(config, pendingCommits);
                AbbreviatedObjectId shortId = targetCommit.abbreviate(7);
                String branchName = config.translationBranchTemplate().replace("<upstream-short-sha>", shortId.name());

                checkoutTranslationBranch(origin, config.originBranch(), branchName);
                MergeResult mergeResult = origin.merge()
                        .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                        .setCommit(true)
                        .include(targetCommit)
                        .call();

                ObjectId translationHead = resolveRequired(origin.getRepository(), "refs/heads/" + branchName);
                DiffMetadata metadata = diffAnalyzer.analyze(origin.getRepository(), originHead, translationHead);

                return new GitWorkflowResult(upstreamDir, originDir, branchName, targetCommit.getName(), shortId.name(), metadata, mergeResult.getMergeStatus());
            }
        } catch (IOException | GitAPIException e) {
            throw new GitWorkflowException("Failed to prepare translation branch", e);
        }
    }

    private Path cloneFresh(URI uri, String prefix) throws GitAPIException, IOException {
        Path directory = Files.createTempDirectory(workspaceRoot, prefix + "-");
        try (Git ignored = Git.cloneRepository()
                .setURI(uri.toString())
                .setDirectory(directory.toFile())
                .setCloneAllBranches(true)
                .call()) {
            // clone closed via try-with-resources
        }
        return directory;
    }

    private void ensureBaseBranchCheckedOut(Git origin, String branch) throws GitAPIException {
        try {
            origin.checkout().setName(branch).call();
        } catch (RefNotFoundException ex) {
            origin.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint("origin/" + branch)
                    .call();
        }
    }

    private void configureUpstreamRemote(Git origin, URI upstream) throws IOException, GitAPIException {
        Repository repository = origin.getRepository();
        StoredConfig config = repository.getConfig();
        config.setString("remote", "upstream", "url", upstream.toString());
        config.setStringList("remote", "upstream", "fetch", List.of(
                "+refs/heads/*:refs/remotes/upstream/*"
        ));
        config.save();
        origin.fetch().setRemote("upstream").call();
    }

    private ObjectId fetchUpstreamHead(Git origin, String branch) throws GitAPIException, IOException {
        origin.fetch().setRemote("upstream").call();
        return resolveRequired(origin.getRepository(), "refs/remotes/upstream/" + branch);
    }

    private List<RevCommit> findPendingUpstreamCommits(Repository repository, ObjectId upstreamHead, ObjectId originHead)
            throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit upstreamCommit = walk.parseCommit(upstreamHead);
            RevCommit originCommit = walk.parseCommit(originHead);
            walk.markStart(upstreamCommit);
            walk.markUninteresting(originCommit);
            List<RevCommit> commits = new java.util.ArrayList<>();
            for (RevCommit commit : walk) {
                commits.add(commit);
            }
            commits.sort((a, b) -> Integer.compare(b.getCommitTime(), a.getCommitTime()));
            return commits;
        }
    }

    private RevCommit selectTargetCommit(Config config, List<RevCommit> pendingCommits) {
        Optional<String> targetSha = config.translationTargetSha();
        if (targetSha.isPresent()) {
            String needle = targetSha.get().toLowerCase();
            return pendingCommits.stream()
                    .filter(commit -> commit.getName().startsWith(needle) || commit.getId().abbreviate(needle.length()).name().equalsIgnoreCase(needle))
                    .findFirst()
                    .orElseThrow(() -> new GitWorkflowException("TRANSLATION_TARGET_SHA does not match pending commits"));
        }

        if (config.mode().isDev() && config.since().isPresent()) {
            String since = config.since().get().toLowerCase();
            List<RevCommit> filtered = pendingCommits.stream()
                    .filter(commit -> commit.getName().startsWith(since) || commit.getId().abbreviate(since.length()).name().equalsIgnoreCase(since))
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                return filtered.get(0);
            }
        }

        return pendingCommits.get(0);
    }

    private void checkoutTranslationBranch(Git origin, String baseBranch, String branchName) throws GitAPIException {
        origin.checkout()
                .setName(baseBranch)
                .call();

        origin.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .setStartPoint("refs/heads/" + baseBranch)
                .setUpstreamMode(SetupUpstreamMode.NOTRACK)
                .call();
    }

    private ObjectId resolveRequired(Repository repository, String ref) throws IOException {
        ObjectId resolved = repository.resolve(ref);
        if (resolved == null) {
            throw new GitWorkflowException("Unable to resolve ref: " + ref);
        }
        return resolved;
    }
}
