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
 * Cleans up merge conflicts in translated documents when the upstream change only deletes content.
 */
public class ConflictCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictCleanupService.class);

    private final ConflictDetector conflictDetector;

    public ConflictCleanupService() {
        this(new ConflictDetector());
    }

    ConflictCleanupService(ConflictDetector conflictDetector) {
        this.conflictDetector = Objects.requireNonNull(conflictDetector, "conflictDetector");
    }

    /**
     * Attempts to resolve merge conflicts where the upstream side removed the conflicting block entirely.
     *
     * @param repositoryRoot the root directory of the origin repository workspace
     * @return list of file paths that were auto-resolved
     */
    public List<String> cleanDeletionConflicts(Path repositoryRoot) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (!Files.isDirectory(repositoryRoot)) {
            return List.of();
        }

        try (Git git = Git.open(repositoryRoot.toFile())) {
            Status status = git.status().call();
            if (status.getConflicting().isEmpty()) {
                return List.of();
            }

            List<String> resolved = new ArrayList<>();
            for (String path : status.getConflicting()) {
                Path file = repositoryRoot.resolve(path);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Optional<ConflictResolutionPlan> plan = detectConflict(file);
                if (plan.isEmpty()) {
                    continue;
                }
                if (!isDeletionOnly(plan.get())) {
                    continue;
                }
                writeResolvedContent(file, plan.get());
                git.add().addFilepattern(path).call();
                resolved.add(path);
                LOGGER.info("Auto-resolved deletion-only conflict in {}", path);
            }
            return List.copyOf(resolved);
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
}

