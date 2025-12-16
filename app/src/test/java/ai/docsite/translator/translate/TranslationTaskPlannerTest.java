package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.GitWorkflowResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationTaskPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void plansTasksFromUpstreamDiffAndKeepsExistingTranslation() throws Exception {
        RepoInfo upstream = createRepo(tempDir.resolve("upstream"), "docs/example.md",
                "Heading\n旧英語1\n旧英語2\n");
        String baseUpstreamSha = upstream.sha();
        upstream.updateFile("docs/example.md",
                "Heading\nNew line 1\nNew line 2\n## Alternatives\n- Colima\n");
        RepoInfo origin = createRepo(tempDir.resolve("origin"), "docs/example.md",
                "Heading\n旧訳1\n旧訳2\n");

        DiffMetadata metadata = new DiffMetadata(List.of(new FileChange("docs/example.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseUpstreamSha, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        List<TranslationTask> tasks = planner.plan(result, 0);

        assertThat(tasks).hasSize(1);
        TranslationTask task = tasks.get(0);
        assertThat(task.filePath()).isEqualTo("docs/example.md");
        assertThat(task.sourceLines()).contains("New line 1", "New line 2", "## Alternatives");
        assertThat(task.existingTranslationLines().subList(0, 3)).containsExactly("Heading", "旧訳1", "旧訳2");
        assertThat(task.segments()).anySatisfy(segment -> {
            assertThat(segment.startLine()).isLessThan(3);
            assertThat(segment.endLineExclusive()).isGreaterThanOrEqualTo(5);
        });
    }

    @Test
    void respectsMaximumFilesPerRun() throws Exception {
        RepoInfo upstream = createRepo(tempDir.resolve("upstream-limit"), "docs/a.md",
                "Line A\nOld content that needs significant updating\n");
        String baseUpstreamSha = upstream.sha();
        try (Git git = Git.open(upstream.path().toFile())) {
            Path fileA = upstream.path().resolve("docs/a.md");
            Files.writeString(fileA, "Line A\nUpdated with substantially different text that requires translation\n");
            Path fileB = upstream.path().resolve("docs/b.md");
            Files.createDirectories(fileB.getParent());
            Files.writeString(fileB, "Line B\nNew content with meaningful changes\n");
            git.add().addFilepattern("docs/a.md").addFilepattern("docs/b.md").call();
            RevCommit commit = git.commit().setMessage("update docs").call();
            upstream.setCommit(commit.getName());
        }
        RepoInfo origin = createRepo(tempDir.resolve("origin-limit"), "docs/a.md",
                "Line A\n旧訳\n");
        appendFile(origin, "docs/b.md", "Line B\n旧訳\n");

        DiffMetadata metadata = new DiffMetadata(List.of(
                new FileChange("docs/a.md", ChangeCategory.DOCUMENT_UPDATED),
                new FileChange("docs/b.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseUpstreamSha, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();

        List<TranslationTask> limited = planner.plan(result, 1);
        assertThat(limited).hasSize(1);
        assertThat(limited.get(0).filePath()).isEqualTo("docs/a.md");

        List<TranslationTask> all = planner.plan(result, 0);
        assertThat(all).hasSize(2);
    }

    @Test
    void splitsLargeDocumentsIntoChunks() throws Exception {
        StringBuilder english = new StringBuilder();
        StringBuilder japanese = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            english.append("Line ").append(i).append('\n');
            japanese.append("訳 ").append(i).append('\n');
        }
        RepoInfo upstream = createRepo(tempDir.resolve("upstream-large"), "docs/long.md", english.toString());
        String baseUpstreamShaLarge = upstream.sha();
        for (int i = 0; i < 200; i++) {
            english.append("追加 ").append(i).append('\n');
        }
        upstream.updateFile("docs/long.md", english.toString());
        RepoInfo origin = createRepo(tempDir.resolve("origin-large"), "docs/long.md", japanese.toString());

        DiffMetadata metadata = new DiffMetadata(List.of(new FileChange("docs/long.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseUpstreamShaLarge, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        List<TranslationTask> tasks = planner.plan(result, 0);

        assertThat(tasks).hasSize(1);
        TranslationTask task = tasks.get(0);
        assertThat(task.segments())
                .hasSizeGreaterThan(1)
                .allSatisfy(segment -> assertThat(segment.endLineExclusive() - segment.startLine()).isLessThanOrEqualTo(120));
    }

    @Test
    void returnsEmptyWhenNoEligibleChanges() {
        DiffMetadata metadata = new DiffMetadata(List.of());
        GitWorkflowResult result = new GitWorkflowResult(tempDir.resolve("upstream"), tempDir.resolve("origin"),
                "", "", "", "", "", metadata, MergeStatus.ALREADY_UP_TO_DATE);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        assertThat(planner.plan(result, 5)).isEmpty();
    }

    @Test
    void detectsConflictMarkersAndRecordsWarning() throws Exception {
        RepoInfo upstream = createRepo(tempDir.resolve("up-conflict"), "docs/conflict.md", "Base\n");
        String baseSha = upstream.sha();
        upstream.updateFile("docs/conflict.md", "<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> branch\n");
        RepoInfo origin = createRepo(tempDir.resolve("origin-conflict"), "docs/conflict.md", "Base\n");

        DiffMetadata metadata = new DiffMetadata(List.of(new FileChange("docs/conflict.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseSha, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        TranslationTaskPlanner.PlanResult planResult = planner.planWithDiagnostics(result, 0);

        assertThat(planResult.tasks()).isEmpty();
        assertThat(planResult.conflictFiles()).containsExactly("docs/conflict.md");
    }

    @Test
    void skipsTranslationForTypoFixes() throws Exception {
        RepoInfo upstream = createRepo(tempDir.resolve("up-typo"), "docs/example.md",
                "/*Get the list of availabel bootswatch themes*/\n");
        String baseSha = upstream.sha();
        upstream.updateFile("docs/example.md",
                "/*Get the list of available bootswatch themes*/\n");
        RepoInfo origin = createRepo(tempDir.resolve("origin-typo"), "docs/example.md",
                "/*ブートスウォッチテーマのリストを取得します（スペル間違い）*/\n");

        DiffMetadata metadata = new DiffMetadata(List.of(new FileChange("docs/example.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseSha, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        List<TranslationTask> tasks = planner.plan(result, 0);

        assertThat(tasks).isEmpty();
    }

    @Test
    void includesTranslationForSubstantialChanges() throws Exception {
        RepoInfo upstream = createRepo(tempDir.resolve("up-substantial"), "docs/example.md",
                "Original sentence with some content.\n");
        String baseSha = upstream.sha();
        upstream.updateFile("docs/example.md",
                "Completely rewritten sentence with different meaning and new information.\n");
        RepoInfo origin = createRepo(tempDir.resolve("origin-substantial"), "docs/example.md",
                "元の文章です。\n");

        DiffMetadata metadata = new DiffMetadata(List.of(new FileChange("docs/example.md", ChangeCategory.DOCUMENT_UPDATED)));
        GitWorkflowResult result = new GitWorkflowResult(upstream.path(), origin.path(),
                "sync-" + upstream.shortSha(), upstream.sha(), upstream.shortSha(), baseSha, origin.sha(), metadata, MergeStatus.MERGED);

        TranslationTaskPlanner planner = new TranslationTaskPlanner();
        List<TranslationTask> tasks = planner.plan(result, 0);

        assertThat(tasks).hasSize(1);
    }

    private RepoInfo createRepo(Path directory, String filePath, String content) throws Exception {
        Files.createDirectories(directory);
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            configureUser(git);
            Path file = directory.resolve(filePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            git.add().addFilepattern(filePath).call();
            RevCommit commit = git.commit().setMessage("init").call();
            return new RepoInfo(directory, commit.getName());
        }
    }

    private void appendFile(RepoInfo repoInfo, String relativePath, String content) throws Exception {
        repoInfo.updateFile(relativePath, content);
    }

    private void configureUser(Git git) throws IOException {
        git.getRepository().getConfig().setString("user", null, "name", "Test");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        git.getRepository().getConfig().save();
    }

    private static final class RepoInfo {
        private final Path path;
        private String sha;
        private String previousSha;

        RepoInfo(Path path, String sha) {
            this.path = path;
            this.sha = sha;
            this.previousSha = sha;
        }

        void updateFile(String relativePath, String content) throws Exception {
            try (Git git = Git.open(path.toFile())) {
                Path file = path.resolve(relativePath);
                Files.createDirectories(file.getParent());
                Files.writeString(file, content);
                git.add().addFilepattern(relativePath).call();
                RevCommit commit = git.commit().setMessage("update " + relativePath).call();
                previousSha = sha;
                sha = commit.getName();
            }
        }

        Path path() {
            return path;
        }

        void setCommit(String newSha) {
            previousSha = sha;
            sha = newSha;
        }

        String sha() {
            return sha;
        }

        String shortSha() {
            return sha.substring(0, Math.min(7, sha.length()));
        }
    }
}
