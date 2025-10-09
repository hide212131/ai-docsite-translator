package ai.docsite.translator.translate.conflict;

import ai.docsite.translator.git.GitWorkflowException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles merge conflicts that remain after automated translation updates.
 *
 * <p>Deletion-only conflicts are auto-resolved by dropping the removed segment. Non-document
 * conflicts are staged as-is (keeping conflict markers) so the workflow can continue while the
 * pending conflict is surfaced to humans via the pull request summary.</p>
 */
public class ConflictCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictCleanupService.class);
    private static final List<String> DOCUMENT_EXTENSIONS = List.of("md", "mdx", "txt", "html");

    private final ConflictDetector conflictDetector;

    public ConflictCleanupService() {
        this(new ConflictDetector());
    }

    ConflictCleanupService(ConflictDetector conflictDetector) {
        this.conflictDetector = Objects.requireNonNull(conflictDetector, "conflictDetector");
    }

    /**
     * Attempts to resolve any merge conflicts that remain in the working tree.
     *
     * @param repositoryRoot the root directory of the origin repository workspace
     * @return summary containing auto-resolved and forced-merge paths
     */
    public Result cleanConflicts(Path repositoryRoot) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (!Files.isDirectory(repositoryRoot)) {
            return Result.empty();
        }

        try (Git git = Git.open(repositoryRoot.toFile())) {
            Status status = git.status().call();
            if (status.getConflicting().isEmpty()) {
                return Result.empty();
            }

            List<String> resolved = new ArrayList<>();
            List<String> forced = new ArrayList<>();
            List<String> remaining = new ArrayList<>();
            for (String path : status.getConflicting()) {
                Path file = repositoryRoot.resolve(path);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Optional<ConflictResolutionPlan> plan = detectConflict(file);
                if (plan.isEmpty()) {
                    if (!isDocument(path)) {
                        stageForced(git, path);
                        forced.add(path);
                    } else {
                        remaining.add(path);
                    }
                    continue;
                }
                if (!isDeletionOnly(plan.get())) {
                    if (!isDocument(path)) {
                        stageForced(git, path);
                        forced.add(path);
                    } else {
                        remaining.add(path);
                    }
                    continue;
                }
                writeResolvedContent(file, plan.get());
                git.add().addFilepattern(path).call();
                resolved.add(path);
                LOGGER.info("Auto-resolved deletion-only conflict in {}", path);
            }
            return new Result(List.copyOf(resolved), List.copyOf(forced), List.copyOf(remaining));
        } catch (GitAPIException | IOException ex) {
            throw new GitWorkflowException("Failed to clean merge conflicts", ex);
        }
    }

    private Optional<ConflictResolutionPlan> detectConflict(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return conflictDetector.detect(lines);
    }

    private boolean isDeletionOnly(ConflictResolutionPlan plan) {
        return plan.blocks().stream().allMatch(block -> block.incomingLines().isEmpty());
    }

    private void writeResolvedContent(Path file, ConflictResolutionPlan plan) throws IOException {
        Files.write(file, plan.baseLines(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private boolean isDocument(String path) {
        int idx = path.lastIndexOf('.') + 1;
        if (idx <= 0 || idx >= path.length()) {
            return false;
        }
        String ext = path.substring(idx).toLowerCase();
        return DOCUMENT_EXTENSIONS.contains(ext);
    }

    private void stageForced(Git git, String path) throws GitAPIException {
        git.add().addFilepattern(path).call();
        LOGGER.warn("Force-staged {} with conflict markers intact", path);
    }

    public record Result(List<String> resolvedConflicts,
                         List<String> forcedMergeConflicts,
                         List<String> remainingConflicts) {

        public Result {
            resolvedConflicts = List.copyOf(resolvedConflicts == null ? List.of() : resolvedConflicts);
            forcedMergeConflicts = List.copyOf(forcedMergeConflicts == null ? List.of() : forcedMergeConflicts);
            remainingConflicts = List.copyOf(remainingConflicts == null ? List.of() : remainingConflicts);
        }

        public static Result empty() {
            return new Result(List.of(), List.of(), List.of());
        }
    }
}
