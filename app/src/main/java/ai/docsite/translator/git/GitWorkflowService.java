package ai.docsite.translator.git;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        this(Path.of(System.getProperty("user.dir"), "workspace"), new DiffAnalyzer());
    }

    public GitWorkflowResult prepareSyncBranch(Config config) {
        Objects.requireNonNull(config, "config");
        try {
            Files.createDirectories(workspaceRoot);
            Path upstreamDir = cloneFresh(config, config.upstreamUrl(), "upstream");
            Path originDir = cloneFresh(config, config.originUrl(), "origin");

            try (Git origin = Git.open(originDir.toFile())) {
                ensureBaseBranchCheckedOut(origin, config.originBranch());
                configureUpstreamRemote(origin, upstreamDir.toUri());

                ObjectId upstreamHead = fetchUpstreamHead(origin, UPSTREAM_MAIN_BRANCH);
                ObjectId originHead = resolveRequired(origin.getRepository(), "refs/heads/" + config.originBranch());

                List<RevCommit> pendingCommits = findPendingUpstreamCommits(origin.getRepository(), upstreamHead, originHead);
                if (pendingCommits.isEmpty()) {
                    LOGGER.info("No untranslated commits detected");
                    return GitWorkflowResult.empty(upstreamDir, originDir, originHead.getName());
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
                ObjectId baseUpstreamCommit = findBaseUpstreamCommit(origin.getRepository(), targetCommit, originHead);
                DiffMetadata metadata = mergeResult.getMergeStatus().isSuccessful()
                        ? diffAnalyzer.analyze(origin.getRepository(), originHead, translationHead)
                        : diffAnalyzer.analyzeWorkingTree(origin.getRepository(), originHead);
                metadata = filterMetadata(metadata, config);

                return new GitWorkflowResult(upstreamDir, originDir, branchName, targetCommit.getName(), shortId.name(),
                        baseUpstreamCommit.getName(), originHead.getName(), metadata, mergeResult.getMergeStatus());
            }
        } catch (IOException | GitAPIException e) {
            throw new GitWorkflowException("Failed to prepare translation branch", e);
        }
    }

    private Path cloneFresh(Config config, URI uri, String prefix) throws GitAPIException, IOException {
        Path directory = determineCloneDirectory(config.mode(), prefix);
        if (Files.exists(directory)) {
            deleteRecursively(directory);
        }
        Files.createDirectories(directory);
        try (Git ignored = Git.cloneRepository()
                .setURI(uri.toString())
                .setDirectory(directory.toFile())
                .setCloneAllBranches(true)
                .call()) {
            // clone closed via try-with-resources
        }
        return directory;
    }

    private Path determineCloneDirectory(Mode mode, String prefix) throws IOException {
        if (mode.isDev()) {
            Files.createDirectories(workspaceRoot);
            return workspaceRoot.resolve(prefix);
        }
        Files.createDirectories(workspaceRoot);
        return Files.createTempDirectory(workspaceRoot, prefix + "-");
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to delete " + path, ex);
                }
            });
        }
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

    private ObjectId findBaseUpstreamCommit(Repository repository, RevCommit targetCommit, ObjectId originHead) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit originCommit = walk.parseCommit(originHead);
            RevCommit upstreamCommit = walk.parseCommit(targetCommit);
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(upstreamCommit);
            walk.markStart(originCommit);
            RevCommit base = walk.next();
            if (base != null) {
                return base.getId();
            }
        }
        if (targetCommit.getParentCount() > 0) {
            return targetCommit.getParent(0).getId();
        }
        return targetCommit.getId();
    }

    private List<RevCommit> findPendingUpstreamCommits(Repository repository, ObjectId upstreamHead, ObjectId originHead)
            throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit upstreamCommit = walk.parseCommit(upstreamHead);
            RevCommit originCommit = walk.parseCommit(originHead);
            walk.markStart(upstreamCommit);
            walk.markUninteresting(originCommit);
            List<RevCommit> commits = new ArrayList<>();
            for (RevCommit commit : walk) {
                commits.add(commit);
            }
            commits.sort((a, b) -> Integer.compare(b.getCommitTime(), a.getCommitTime()));
            return commits;
        }
    }

    private DiffMetadata filterMetadata(DiffMetadata metadata, Config config) {
        if (metadata == null) {
            return DiffMetadata.empty();
        }
        List<FileChange> filtered = new ArrayList<>();
        Set<String> allowedExtensions = config.documentExtensions();
        List<String> includePaths = config.translationIncludePaths();

        for (FileChange change : metadata.changes()) {
            if (change.category() == ChangeCategory.NON_DOCUMENT) {
                filtered.add(change);
                continue;
            }

            String normalizedPath = normalizePath(change.path());
            if (!includePaths.isEmpty() && !isUnderIncludedPath(normalizedPath, includePaths)) {
                continue;
            }

            if (!allowedExtensions.isEmpty()) {
                String extension = extensionOf(normalizedPath);
                if (!allowedExtensions.contains(extension)) {
                    continue;
                }
            }

            filtered.add(change);
        }

        return new DiffMetadata(filtered);
    }

    private boolean isUnderIncludedPath(String path, List<String> includePaths) {
        for (String include : includePaths) {
            if (include.isBlank()) {
                continue;
            }
            if (path.equals(include) || path.startsWith(include + "/")) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private String extensionOf(String path) {
        int idx = path.lastIndexOf('.') + 1;
        if (idx <= 0 || idx == path.length()) {
            return "";
        }
        return path.substring(idx).toLowerCase(Locale.ROOT);
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
