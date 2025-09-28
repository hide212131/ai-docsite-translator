package ai.docsite.translator.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.Optional;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles staging verification and commit creation for translated documents.
 */
public class CommitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommitService.class);
    private static final String DEFAULT_USER_NAME = "DocSite Translator";
    private static final String DEFAULT_USER_EMAIL = "translator@example.com";

    public CommitResult commitTranslatedFiles(Path repositoryRoot,
                                              String targetShortSha,
                                              List<String> translatedFiles,
                                              boolean dryRun) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        Objects.requireNonNull(targetShortSha, "targetShortSha");
        Objects.requireNonNull(translatedFiles, "translatedFiles");
        if (translatedFiles.isEmpty()) {
            return CommitResult.noChanges();
        }
        if (!Files.isDirectory(repositoryRoot.resolve(".git"))) {
            LOGGER.warn("Skipping commit because {} is not a Git repository", repositoryRoot);
            return CommitResult.noChanges();
        }

        Set<String> normalizedFiles = translatedFiles.stream()
                .map(CommitService::normalizePath)
                .collect(Collectors.toCollection(TreeSet::new));

        try (Git git = Git.open(repositoryRoot.toFile())) {
            for (String file : normalizedFiles) {
                git.add().addFilepattern(file).call();
            }
            Status status = git.status().call();
            boolean hasChanges = !status.isClean();
            String commitMessage = buildCommitMessage(targetShortSha, normalizedFiles);
            if (!hasChanges) {
                LOGGER.info("No staged changes detected for commit");
                return new CommitResult(false, false, Optional.empty(), commitMessage, List.copyOf(normalizedFiles));
            }
            if (dryRun) {
                LOGGER.info("Dry-run commit preview:\n{}", commitMessage);
                return new CommitResult(true, false, Optional.empty(), commitMessage, List.copyOf(normalizedFiles));
            }
            ensureUserConfigured(git);
            RevCommit commit = git.commit().setMessage(commitMessage).call();
            LOGGER.info("Committed {} files as {}", normalizedFiles.size(), commit.getId().abbreviate(7).name());
            return new CommitResult(true, true, Optional.of(commit.getId().getName()), commitMessage, List.copyOf(normalizedFiles));
        } catch (GitAPIException | IOException ex) {
            throw new GitWorkflowException("Failed to commit translated files", ex);
        }
    }

    public boolean pushTranslationBranch(Path repositoryRoot, String branchName, Optional<String> githubToken) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branchName must not be blank");
        }
        if (!Files.isDirectory(repositoryRoot.resolve(".git"))) {
            LOGGER.warn("Skipping push because {} is not a Git repository", repositoryRoot);
            return false;
        }
        try (Git git = Git.open(repositoryRoot.toFile())) {
            RefSpec refSpec = new RefSpec("refs/heads/" + branchName + ":refs/heads/" + branchName);
            var pushCommand = git.push()
                    .setRemote("origin")
                    .setRefSpecs(refSpec);
            githubToken.filter(token -> !token.isBlank())
                    .ifPresent(token -> pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token)));
            Iterable<PushResult> results = pushCommand.call();
            boolean success = true;
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    RemoteRefUpdate.Status status = update.getStatus();
                    if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                        success = false;
                        LOGGER.warn("Remote update for {} returned status {}", update.getRemoteName(), status);
                    }
                }
            }
            if (success) {
                LOGGER.info("Pushed branch {} to origin", branchName);
            }
            return success;
        } catch (GitAPIException | IOException ex) {
            throw new GitWorkflowException("Failed to push translation branch", ex);
        }
    }

    private static void ensureUserConfigured(Git git) throws IOException {
        org.eclipse.jgit.lib.StoredConfig config = git.getRepository().getConfig();
        boolean updated = false;
        if (config.getString("user", null, "name") == null) {
            config.setString("user", null, "name", DEFAULT_USER_NAME);
            updated = true;
        }
        if (config.getString("user", null, "email") == null) {
            config.setString("user", null, "email", DEFAULT_USER_EMAIL);
            updated = true;
        }
        if (updated) {
            config.save();
        }
    }

    private String buildCommitMessage(String shortSha, Set<String> files) {
        List<String> lines = new ArrayList<>();
        lines.add("docs: sync-" + shortSha);
        if (!files.isEmpty()) {
            lines.add("");
            for (String file : files) {
                lines.add("- " + file);
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    public record CommitResult(boolean changesDetected,
                               boolean committed,
                               Optional<String> commitSha,
                               String commitMessage,
                               List<String> files) {

        public CommitResult {
            commitSha = commitSha == null ? Optional.empty() : commitSha;
            commitMessage = Objects.requireNonNull(commitMessage, "commitMessage");
            files = List.copyOf(Objects.requireNonNull(files, "files"));
        }

        public static CommitResult noChanges() {
            return new CommitResult(false, false, Optional.empty(), "", List.of());
        }
    }
}
