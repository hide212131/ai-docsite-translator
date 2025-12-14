package ai.docsite.translator.translate.conflict;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.translate.TranslationMode;
import ai.docsite.translator.translate.TranslationResult;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictCleanupServiceTest {

    @TempDir
    Path tempDir;
    
    private Git createGitRepoWithConflict(Path repoDir, String filePath, List<String> headContent, List<String> branchContent) throws Exception {
        Files.createDirectories(repoDir);
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        git.getRepository().getConfig().setString("user", null, "name", "Test User");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        git.getRepository().getConfig().save();
        
        // Create initial file
        Path file = repoDir.resolve(filePath);
        Files.createDirectories(file.getParent());
        Files.write(file, List.of("# Initial"), StandardCharsets.UTF_8);
        git.add().addFilepattern(filePath).call();
        git.commit().setMessage("initial").call();
        
        // Create branch with one version
        git.branchCreate().setName("branch1").call();
        git.checkout().setName("branch1").call();
        Files.write(file, branchContent, StandardCharsets.UTF_8);
        git.add().addFilepattern(filePath).call();
        git.commit().setMessage("branch change").call();
        
        // Checkout master and add different version
        git.checkout().setName("master").call();
        Files.write(file, headContent, StandardCharsets.UTF_8);
        git.add().addFilepattern(filePath).call();
        git.commit().setMessage("master change").call();
        
        // Merge to create conflict
        try {
            git.merge().include(git.getRepository().resolve("branch1")).call();
        } catch (Exception e) {
            // Expected merge conflict
        }
        
        return git;
    }

    @Test
    void resolvesDocumentConflictViaTranslation() throws Exception {
        // Setup: Create a mock TranslationService that translates English to "Japanese"
        TranslationService mockTranslationService = new TranslationService() {
            @Override
            public TranslationResult translateTask(TranslationTask task, TranslationMode mode) {
                List<String> translated = task.sourceLines().stream()
                        .map(line -> "[JA] " + line)
                        .toList();
                return new TranslationResult(task.filePath(), translated);
            }
        };

        ConflictCleanupService service = new ConflictCleanupService(
                new ConflictDetector(),
                mockTranslationService,
                TranslationMode.PRODUCTION
        );

        // Create a git repository with a real merge conflict
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("docs/README.md");
        
        try (Git git = createGitRepoWithConflict(repoDir, "docs/README.md",
                List.of("# Title", "New English content"),
                List.of("# Title", "Old Japanese content"))) {

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Document conflict was resolved
            assertThat(result.resolvedConflicts()).contains("docs/README.md");
            assertThat(result.forcedMergeConflicts()).isEmpty();
            assertThat(result.remainingConflicts()).isEmpty();

            // Verify: File content is resolved (no conflict markers)
            List<String> resolvedContent = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(resolvedContent).doesNotContain("<<<<<<< HEAD");
            assertThat(resolvedContent).doesNotContain("=======");
            assertThat(resolvedContent).doesNotContain(">>>>>>> branch1");
            assertThat(resolvedContent).contains("[JA] Old Japanese content");
        }
    }

    @Test
    void forceStagesNonDocumentConflicts() throws Exception {
        ConflictCleanupService service = new ConflictCleanupService();

        // Create a git repository with a conflicted non-document file
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("src/Main.java");
        
        try (Git git = createGitRepoWithConflict(repoDir, "src/Main.java",
                List.of("public class Main {", "    // New code", "}"),
                List.of("public class Main {", "    // Old code", "}"))) {

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Non-document conflict was force-staged
            assertThat(result.resolvedConflicts()).isEmpty();
            assertThat(result.forcedMergeConflicts()).contains("src/Main.java");
            assertThat(result.remainingConflicts()).isEmpty();

            // Verify: File content still has conflict markers
            List<String> content = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(content).anyMatch(line -> line.startsWith("<<<<<<< HEAD"));
            assertThat(content).anyMatch(line -> line.equals("======="));
            assertThat(content).anyMatch(line -> line.startsWith(">>>>>>>"));
        }
    }

    @Test
    void resolvesDeletionOnlyConflicts() throws Exception {
        ConflictCleanupService service = new ConflictCleanupService();

        // Create a git repository with a deletion conflict
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("docs/README.md");
        
        try (Git git = createGitRepoWithConflict(repoDir, "docs/README.md",
                List.of("# Title", "Content to be deleted", "Remaining content"),
                List.of("# Title", "Remaining content"))) {

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Deletion conflict was resolved
            assertThat(result.resolvedConflicts()).contains("docs/README.md");
            assertThat(result.forcedMergeConflicts()).isEmpty();
            assertThat(result.remainingConflicts()).isEmpty();

            // Verify: File content is resolved (deleted content removed)
            List<String> resolvedContent = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(resolvedContent).doesNotContain("<<<<<<< HEAD");
            assertThat(resolvedContent).doesNotContain("Content to be deleted");
        }
    }

    @Test
    void leavesDocumentConflictUnresolvedWhenTranslationServiceNotAvailable() throws Exception {
        ConflictCleanupService service = new ConflictCleanupService();

        // Create a git repository with a conflicted document file
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("docs/README.md");
        
        try (Git git = createGitRepoWithConflict(repoDir, "docs/README.md",
                List.of("# Title", "New content"),
                List.of("# Title", "Old content"))) {

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Document conflict remained unresolved (no translation service)
            assertThat(result.resolvedConflicts()).isEmpty();
            assertThat(result.forcedMergeConflicts()).isEmpty();
            assertThat(result.remainingConflicts()).contains("docs/README.md");

            // Verify: File content still has conflict markers
            List<String> content = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(content).anyMatch(line -> line.startsWith("<<<<<<< HEAD"));
            assertThat(content).anyMatch(line -> line.equals("======="));
            assertThat(content).anyMatch(line -> line.startsWith(">>>>>>>"));
        }
    }

    @Test
    void returnsEmptyResultWhenNoConflicts() throws Exception {
        ConflictCleanupService service = new ConflictCleanupService();

        // Create a git repository without conflicts
        Path repoDir = tempDir.resolve("repo");
        Files.createDirectories(repoDir);
        
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Path normalFile = repoDir.resolve("docs/README.md");
            Files.createDirectories(normalFile.getParent());
            Files.write(normalFile, List.of("# Normal file", "No conflicts here"), StandardCharsets.UTF_8);
            git.add().addFilepattern("docs/README.md").call();
            git.commit().setMessage("Initial commit").call();

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: No conflicts detected
            assertThat(result.resolvedConflicts()).isEmpty();
            assertThat(result.forcedMergeConflicts()).isEmpty();
            assertThat(result.remainingConflicts()).isEmpty();
        }
    }

    @Test
    void autoStagesDocumentFileWithNoConflictMarkers() throws Exception {
        // This test covers the scenario where a document file was already resolved by the LLM
        // (conflict markers removed), but git still reports it as conflicting.
        // The file should be auto-staged and added to resolvedConflicts.
        
        ConflictCleanupService service = new ConflictCleanupService();

        // Create a git repository with a conflict
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("docs/README.md");
        
        try (Git git = createGitRepoWithConflict(repoDir, "docs/README.md",
                List.of("# Title", "HEAD content"),
                List.of("# Title", "Incoming content"))) {

            // Manually resolve the conflict by replacing the file content (simulating LLM resolution)
            Files.write(conflictedFile, List.of("# Title", "Resolved content"), StandardCharsets.UTF_8);

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Document file with no conflict markers was auto-staged as resolved
            assertThat(result.resolvedConflicts()).contains("docs/README.md");
            assertThat(result.forcedMergeConflicts()).isEmpty();
            assertThat(result.remainingConflicts()).isEmpty();

            // Verify: File content has no conflict markers
            List<String> resolvedContent = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(resolvedContent).contains("# Title", "Resolved content");
            assertThat(resolvedContent).doesNotContain("<<<<<<< HEAD");
            assertThat(resolvedContent).doesNotContain("=======");
            assertThat(resolvedContent).doesNotContain(">>>>>>>");
        }
    }

    @Test
    void handlesMultipleConflictBlocks() throws Exception {
        // Setup: Create a mock TranslationService
        TranslationService mockTranslationService = new TranslationService() {
            @Override
            public TranslationResult translateTask(TranslationTask task, TranslationMode mode) {
                List<String> translated = task.sourceLines().stream()
                        .map(line -> "[JA] " + line)
                        .toList();
                return new TranslationResult(task.filePath(), translated);
            }
        };

        ConflictCleanupService service = new ConflictCleanupService(
                new ConflictDetector(),
                mockTranslationService,
                TranslationMode.PRODUCTION
        );

        // Create a git repository with a conflict
        Path repoDir = tempDir.resolve("repo");
        Path conflictedFile = repoDir.resolve("docs/README.md");
        
        try (Git git = createGitRepoWithConflict(repoDir, "docs/README.md",
                List.of("# Title", "First conflict HEAD", "Middle content", "Second conflict HEAD", "End content"),
                List.of("# Title", "First conflict incoming", "Middle content", "Second conflict incoming", "End content"))) {

            // Call the service
            ConflictCleanupService.Result result = service.cleanConflicts(repoDir);

            // Verify: Conflict was resolved
            assertThat(result.resolvedConflicts()).contains("docs/README.md");
            assertThat(result.remainingConflicts()).isEmpty();

            // Verify: File content has no conflict markers
            List<String> resolvedContent = Files.readAllLines(conflictedFile, StandardCharsets.UTF_8);
            assertThat(resolvedContent).doesNotContain("<<<<<<< HEAD");
            assertThat(resolvedContent).doesNotContain("=======");
            assertThat(resolvedContent).doesNotContain(">>>>>>> branch1");
        }
    }
}
