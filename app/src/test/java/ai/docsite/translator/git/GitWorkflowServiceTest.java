package ai.docsite.translator.git;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.LlmProvider;
import ai.docsite.translator.config.LogFormat;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.config.TranslatorConfig;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.translate.TranslationMode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
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

    @Test
    void fallsBackToWorkingTreeDiffWhenMergeConflicts() throws Exception {
        ConflictSetup setup = prepareRepositoriesWithConflict();
        Config config = config(Optional.empty(), setup.upstreamRemote().toUri(), setup.originRemote().toUri());

        GitWorkflowService service = new GitWorkflowService(tempDir.resolve("workspace"), new DiffAnalyzer());
        GitWorkflowResult result = service.prepareSyncBranch(config);

        assertThat(result.mergeStatus()).isEqualTo(MergeStatus.CONFLICTING);
        assertThat(result.diffMetadata().byCategory(ChangeCategory.DOCUMENT_UPDATED))
                .extracting(FileChange::path)
                .contains("README.md");
    }

    private Config config(Optional<String> translationTargetSha) {
        return config(translationTargetSha, repositoryUri("upstream-remote"), repositoryUri("origin-remote"));
    }

    private Config config(Optional<String> translationTargetSha, URI upstreamUri, URI originUri) {
        return new Config(Mode.BATCH,
                upstreamUri,
                originUri,
                "main",
                "sync-<upstream-short-sha>",
                Optional.empty(),
                true,
                TranslationMode.DRY_RUN,
                LogFormat.TEXT,
                new TranslatorConfig(LlmProvider.OLLAMA, "lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0", Optional.of("http://localhost:11434")),
                new Secrets(Optional.empty(), Optional.empty()),
                translationTargetSha,
                0,
                List.of(),
                Set.of(),
                6,
                2,
                60,
                0.3);
    }

    private RepositorySetup prepareRepositories() throws Exception {
        Path upstreamRemote = tempDir.resolve("upstream-remote");
        Path originRemote = tempDir.resolve("origin-remote");

        Files.createDirectories(upstreamRemote);
        Files.createDirectories(originRemote);

        try (Git upstream = Git.init().setDirectory(upstreamRemote.toFile()).setInitialBranch("main").call()) {
            configureUser(upstream);

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

    private ConflictSetup prepareRepositoriesWithConflict() throws Exception {
        Path upstreamRemote = tempDir.resolve("upstream-conflict");
        Path originRemote = tempDir.resolve("origin-conflict");

        Files.createDirectories(upstreamRemote);
        Files.createDirectories(originRemote);

        try (Git upstream = Git.init().setDirectory(upstreamRemote.toFile()).setInitialBranch("main").call()) {
            configureUser(upstream);
            write(upstreamRemote.resolve("README.md"), "Base\n");
            upstream.add().addFilepattern("README.md").call();
            upstream.commit().setMessage("base").call();
        }

        try (Git origin = Git.cloneRepository()
                .setURI(upstreamRemote.toUri().toString())
                .setDirectory(originRemote.toFile())
                .call()) {
            configureUser(origin);
            origin.checkout().setName("main").call();
            write(originRemote.resolve("README.md"), "翻訳版\n");
            origin.add().addFilepattern("README.md").call();
            origin.commit().setMessage("translate readme").call();
        }

        try (Git upstream = Git.open(upstreamRemote.toFile())) {
            configureUser(upstream);
            write(upstreamRemote.resolve("README.md"), "Upstream update\n");
            upstream.add().addFilepattern("README.md").call();
            upstream.commit().setMessage("upstream update").call();
        }

        return new ConflictSetup(upstreamRemote, originRemote);
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void configureUser(Git git) throws IOException {
        git.getRepository().getConfig().setString("user", null, "name", "Test");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        git.getRepository().getConfig().save();
    }

    private URI repositoryUri(String name) {
        return tempDir.resolve(name).toUri();
    }

    private record RepositorySetup(String firstNewShortSha, String latestShortSha) { }

    private record ConflictSetup(Path upstreamRemote, Path originRemote) { }
}
