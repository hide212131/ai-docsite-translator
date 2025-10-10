package ai.docsite.translator.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docsite.translator.git.CommitService.CommitResult;
import ai.docsite.translator.git.GitWorkflowException;
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
        Git.init().setDirectory(remote.toFile()).setBare(true).setInitialBranch("main").call().close();

        try (Git git = Git.open(repository.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
        }

        mutateFile(repository, "docs/push.md", "Updated\n");
        CommitResult result = commitService.commitTranslatedFiles(repository, "def5678", List.of("docs/push.md"), false);
        assertThat(result.committed()).isTrue();

        commitService.pushTranslationBranch(repository, "main", Optional.empty());

        try (Git git = Git.open(remote.toFile())) {
            assertThat(git.getRepository().resolve("refs/heads/main")).isNotNull();
        }
    }

    @Test
    void pushTranslationBranchThrowsWhenRejected() throws Exception {
        Path repository = initializeRepository("docs/conflict.md", "Initial\n");
        Path remote = tempDir.resolve("remote-conflict.git");
        Git.init().setDirectory(remote.toFile()).setBare(true).setInitialBranch("main").call().close();

        try (Git git = Git.open(repository.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
        }

        // First push to establish history.
        mutateFile(repository, "docs/conflict.md", "First update\n");
        CommitResult initial = commitService.commitTranslatedFiles(repository, "abc1234", List.of("docs/conflict.md"), false);
        assertThat(initial.committed()).isTrue();
        commitService.pushTranslationBranch(repository, "main", Optional.empty());

        // Create a remote-only commit to force non-fast-forward rejection.
        try (Git remoteGit = Git.cloneRepository()
                .setURI(remote.toUri().toString())
                .setDirectory(tempDir.resolve("remote-working").toFile())
                .call()) {
            configureUser(remoteGit);
            Path file = remoteGit.getRepository().getWorkTree().toPath().resolve("docs/conflict.md");
            Files.writeString(file, "Remote change\n");
            remoteGit.add().addFilepattern("docs/conflict.md").call();
            remoteGit.commit().setMessage("remote change").call();
            remoteGit.push().call();
        }

        mutateFile(repository, "docs/conflict.md", "Second update\n");
        CommitResult second = commitService.commitTranslatedFiles(repository, "def5678", List.of("docs/conflict.md"), false);
        assertThat(second.committed()).isTrue();

        assertThatThrownBy(() -> commitService.pushTranslationBranch(repository, "main", Optional.empty()))
                .isInstanceOf(GitWorkflowException.class)
                .hasMessageContaining("Failed to push translation branch main");
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
