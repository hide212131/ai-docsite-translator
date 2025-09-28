package ai.docsite.translator.git;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.git.CommitService.CommitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitServiceTest {

    private final CommitService commitService = new CommitService();

    @TempDir
    Path tempDir;

    @Test
    void dryRunReturnsPreviewWithoutCreatingCommit() throws Exception {
        Path repository = initializeRepository("docs/file.md", "Initial\n");
        mutateFile(repository, "docs/file.md", "Updated\n");

        CommitResult result = commitService.commitTranslatedFiles(repository, "abc1234", List.of("docs/file.md"), true);

        assertThat(result.changesDetected()).isTrue();
        assertThat(result.committed()).isFalse();
        assertThat(result.commitSha()).isEmpty();
        assertThat(result.commitMessage()).startsWith("docs: sync-abc1234");
        try (Git git = Git.open(repository.toFile())) {
            Iterable<RevCommit> history = git.log().call();
            assertThat(history).hasSize(1);
        }
    }

    @Test
    void commitCreatesCommitAndReturnsSha() throws Exception {
        Path repository = initializeRepository("docs/guide.md", "Initial\n");
        mutateFile(repository, "docs/guide.md", "Updated\n");

        CommitResult result = commitService.commitTranslatedFiles(repository, "def5678", List.of("docs/guide.md"), false);

        assertThat(result.changesDetected()).isTrue();
        assertThat(result.committed()).isTrue();
        assertThat(result.commitSha()).isPresent();
        assertThat(result.files()).containsExactly("docs/guide.md");
        try (Git git = Git.open(repository.toFile())) {
            RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertThat(head.getFullMessage()).isEqualTo(result.commitMessage());
            assertThat(head.getId().getName()).isEqualTo(result.commitSha().orElseThrow());
        }
    }

    @Test
    void pushTranslationBranchSendsBranchToOrigin() throws Exception {
        Path repository = initializeRepository("docs/push.md", "Initial\n");
        Path remote = tempDir.resolve("remote.git");
        Git.init().setDirectory(remote.toFile()).setBare(true).call().close();

        try (Git git = Git.open(repository.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
        }

        mutateFile(repository, "docs/push.md", "Updated\n");
        CommitResult result = commitService.commitTranslatedFiles(repository, "def5678", List.of("docs/push.md"), false);
        assertThat(result.committed()).isTrue();

        boolean pushed = commitService.pushTranslationBranch(repository, "main", Optional.empty());
        assertThat(pushed).isTrue();

        try (Git git = Git.open(remote.toFile())) {
            assertThat(git.getRepository().resolve("refs/heads/main")).isNotNull();
        }
    }

    private Path initializeRepository(String relativePath, String content) throws Exception {
        Path repoDir = tempDir.resolve("repo-" + relativePath.hashCode());
        Files.createDirectories(repoDir);
        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            configureUser(git);
            Path file = repoDir.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            git.add().addFilepattern(relativePath).call();
            git.commit().setMessage("init").setAuthor("Tester", "test@example.com").call();
        }
        return repoDir;
    }

    private void mutateFile(Path repository, String relativePath, String content) throws Exception {
        try (Git git = Git.open(repository.toFile())) {
            Path file = repository.resolve(relativePath);
            Files.writeString(file, content);
            git.add().addFilepattern(relativePath).call();
        }
    }

    private void configureUser(Git git) throws Exception {
        git.getRepository().getConfig().setString("user", null, "name", "Tester");
        git.getRepository().getConfig().setString("user", null, "email", "tester@example.com");
        git.getRepository().getConfig().save();
    }
}
