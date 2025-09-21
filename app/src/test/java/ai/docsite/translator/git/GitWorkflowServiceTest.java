package ai.docsite.translator.git;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.diff.FileChange;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorkflowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void preparesSyncBranchAndDiffMetadata() throws Exception {
        RepositorySetup setup = prepareRepositories();
        Config config = config(Optional.empty());

        GitWorkflowService service = new GitWorkflowService(tempDir.resolve("workspace"), new DiffAnalyzer());
        GitWorkflowResult result = service.prepareSyncBranch(config);

        assertThat(result.translationBranch()).isEqualTo("sync-" + setup.latestShortSha);
        assertThat(result.mergeStatus().isSuccessful()).isTrue();
        assertThat(result.diffMetadata().byCategory(ChangeCategory.DOCUMENT_NEW))
                .extracting(FileChange::path)
                .contains("docs/guide.md");
        assertThat(result.diffMetadata().byCategory(ChangeCategory.DOCUMENT_UPDATED))
                .extracting(FileChange::path)
                .contains("README.md");
        assertThat(result.diffMetadata().byCategory(ChangeCategory.NON_DOCUMENT))
                .extracting(FileChange::path)
                .contains("build.gradle");
    }

    @Test
    void respectsTranslationTargetShaOverride() throws Exception {
        RepositorySetup setup = prepareRepositories();
        Config config = config(Optional.of(setup.firstNewShortSha));

        GitWorkflowService service = new GitWorkflowService(tempDir.resolve("workspace"), new DiffAnalyzer());
        GitWorkflowResult result = service.prepareSyncBranch(config);

        assertThat(result.translationBranch()).isEqualTo("sync-" + setup.firstNewShortSha);
        assertThat(result.diffMetadata().changes()).extracting(FileChange::path)
                .containsExactly("docs/guide.md");
    }

    private Config config(Optional<String> translationTargetSha) {
        return new Config(Mode.BATCH,
                repositoryUri("upstream-remote"),
                repositoryUri("origin-remote"),
                "main",
                "sync-<upstream-short-sha>",
                Optional.empty(),
                true,
                new Secrets(Optional.empty(), Optional.empty()),
                translationTargetSha);
    }

    private RepositorySetup prepareRepositories() throws Exception {
        Path upstreamRemote = tempDir.resolve("upstream-remote");
        Path originRemote = tempDir.resolve("origin-remote");

        Files.createDirectories(upstreamRemote);
        Files.createDirectories(originRemote);

        try (Git upstream = Git.init().setDirectory(upstreamRemote.toFile()).setInitialBranch("main").call()) {
            upstream.getRepository().getConfig().setString("user", null, "name", "Test");
            upstream.getRepository().getConfig().setString("user", null, "email", "test@example.com");
            upstream.getRepository().getConfig().save();

            write(upstreamRemote.resolve("README.md"), "Base\n");
            upstream.add().addFilepattern("README.md").call();
            RevCommit baseCommit = upstream.commit().setMessage("base").call();

            write(upstreamRemote.resolve("docs/guide.md"), "New guide\n");
            upstream.add().addFilepattern("docs/guide.md").call();
            RevCommit firstNewCommit = upstream.commit().setMessage("add guide").call();

            write(upstreamRemote.resolve("README.md"), "Base updated\n");
            write(upstreamRemote.resolve("build.gradle"), "plugins {}\n");
            upstream.add().addFilepattern("README.md").addFilepattern("build.gradle").call();
            RevCommit latestCommit = upstream.commit().setMessage("update docs and build").call();

            try (Git origin = Git.cloneRepository()
                    .setURI(upstreamRemote.toUri().toString())
                    .setDirectory(originRemote.toFile())
                    .call()) {
                origin.reset().setMode(ResetType.HARD).setRef(baseCommit.getId().getName()).call();
                origin.branchCreate().setName("main").setForce(true).setStartPoint(baseCommit.getId().getName()).call();
                origin.checkout().setName("main").call();
            }

            return new RepositorySetup(firstNewCommit.getName().substring(0, 7), latestCommit.getName().substring(0, 7));
        }
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private URI repositoryUri(String name) {
        return tempDir.resolve(name).toUri();
    }

    private record RepositorySetup(String firstNewShortSha, String latestShortSha) { }
}
