package ai.docsite.translator.translate.conflict;

import ai.docsite.translator.git.GitWorkflowException;
import ai.docsite.translator.translate.TranslationMode;
import ai.docsite.translator.translate.TranslationSegment;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTask;
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
 * <p>Deletion-only conflicts are auto-resolved by dropping the removed segment. Document conflicts
 * are resolved by translating incoming English content and merging with HEAD Japanese content.
 * Non-document conflicts are staged as-is (keeping conflict markers) so the workflow can continue
 * while the pending conflict is surfaced to humans via the pull request summary.</p>
 */
public class ConflictCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictCleanupService.class);
    private static final List<String> DOCUMENT_EXTENSIONS = List.of("md", "mdx", "txt", "html");

    private final ConflictDetector conflictDetector;
    private final TranslationService translationService;
    private final TranslationMode translationMode;

    public ConflictCleanupService() {
        this(new ConflictDetector(), null, TranslationMode.PRODUCTION);
    }

    public ConflictCleanupService(TranslationService translationService, TranslationMode translationMode) {
        this(new ConflictDetector(), translationService, translationMode);
    }

    ConflictCleanupService(ConflictDetector conflictDetector, TranslationService translationService, TranslationMode translationMode) {
        this.conflictDetector = Objects.requireNonNull(conflictDetector, "conflictDetector");
        this.translationService = translationService;
        this.translationMode = translationMode != null ? translationMode : TranslationMode.PRODUCTION;
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
                        LOGGER.warn("Force-staged non-document file with conflict markers intact: {}", path);
                    } else {
                        remaining.add(path);
                        LOGGER.warn("Unresolved document conflict (no conflict markers detected): {}", path);
                    }
                    continue;
                }
                if (isDeletionOnly(plan.get())) {
                    writeResolvedContent(file, plan.get());
                    git.add().addFilepattern(path).call();
                    resolved.add(path);
                    LOGGER.info("Auto-resolved deletion-only conflict in {}", path);
                } else if (isDocument(path) && translationService != null) {
                    // Attempt to resolve document conflict via translation
                    Optional<List<String>> mergedContent = resolveDocumentConflict(plan.get());
                    if (mergedContent.isPresent()) {
                        Files.write(file, mergedContent.get(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        git.add().addFilepattern(path).call();
                        resolved.add(path);
                        LOGGER.info("Auto-resolved document conflict via translation and merge: {}", path);
                    } else {
                        remaining.add(path);
                        LOGGER.warn("Failed to resolve document conflict programmatically: {}", path);
                    }
                } else if (!isDocument(path)) {
                    stageForced(git, path);
                    forced.add(path);
                    LOGGER.warn("Force-staged non-document file with conflict markers intact: {}", path);
                } else {
                    remaining.add(path);
                    LOGGER.warn("Unresolved document conflict (translation service not available): {}", path);
                }
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

    /**
     * Resolves document conflicts by translating incoming content and merging with HEAD.
     *
     * @param plan the conflict resolution plan containing base and conflict blocks
     * @return merged content if successful, empty if translation fails
     */
    private Optional<List<String>> resolveDocumentConflict(ConflictResolutionPlan plan) {
        try {
            List<String> mergedLines = new ArrayList<>(plan.baseLines());
            
            for (ConflictBlock block : plan.blocks()) {
                List<String> incomingLines = block.incomingLines();
                if (incomingLines.isEmpty()) {
                    // Deletion - already handled by baseLines
                    continue;
                }
                
                // Translate the incoming English content to Japanese
                List<String> translatedLines;
                try {
                    translatedLines = translationService.translateTask(
                            new TranslationTask(
                                    "conflict-resolution",
                                    incomingLines,
                                    List.of(),
                                    List.of(new TranslationSegment(0, incomingLines.size()))
                            ),
                            translationMode
                    ).lines();
                } catch (Exception ex) {
                    LOGGER.error("Translation failed for conflict block at line {}: {}", block.startLine(), ex.getMessage());
                    return Optional.empty();
                }
                
                // Merge translated content with HEAD content
                List<String> headLines = block.baseLines();
                List<String> mergedBlock = mergeContentLines(headLines, translatedLines);
                
                // Replace the block in the merged document
                int insertPosition = block.startLine();
                for (int i = 0; i < mergedBlock.size() && insertPosition < mergedLines.size(); i++) {
                    if (insertPosition + i < mergedLines.size()) {
                        mergedLines.set(insertPosition + i, mergedBlock.get(i));
                    }
                }
            }
            
            return Optional.of(mergedLines);
        } catch (Exception ex) {
            LOGGER.error("Failed to resolve document conflict: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Merges HEAD and translated incoming content to produce unified Japanese text.
     * Strategy: Prefer the translated incoming content as it represents the latest upstream changes.
     *
     * @param headLines existing Japanese content from HEAD
     * @param translatedLines newly translated Japanese content from incoming
     * @return merged content
     */
    private List<String> mergeContentLines(List<String> headLines, List<String> translatedLines) {
        // If translated content is available and non-empty, prefer it as it's the latest
        if (translatedLines != null && !translatedLines.isEmpty() && 
            !translatedLines.stream().allMatch(String::isBlank)) {
            return new ArrayList<>(translatedLines);
        }
        
        // Fallback to HEAD if translation is empty
        if (headLines != null && !headLines.isEmpty()) {
            return new ArrayList<>(headLines);
        }
        
        // Last resort: return what we have
        return translatedLines != null ? new ArrayList<>(translatedLines) : new ArrayList<>(headLines != null ? headLines : List.of());
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
