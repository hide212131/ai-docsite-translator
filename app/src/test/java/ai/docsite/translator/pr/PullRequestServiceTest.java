package ai.docsite.translator.pr;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.LlmProvider;
import ai.docsite.translator.config.LogFormat;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.config.TranslatorConfig;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.git.GitWorkflowResult;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.junit.jupiter.api.Test;

class PullRequestServiceTest {

    @Test
    void preparesDraftWithLinksAndWarnings() {
        PullRequestService service = new PullRequestService(new PullRequestComposer());
        Config config = config();
        GitWorkflowResult workflowResult = workflowResult();

        PullRequestService.PullRequestDraft draft = service.prepareDraft(config,
                workflowResult,
                List.of("docs/guide.md"),
                Optional.of("feedface"),
                List.of("docs/conflict.md"),
                List.of("docs/failed.md"));

        assertThat(draft.title()).isEqualTo("docs: sync-abc1234");
        assertThat(draft.body()).contains("https://github.com/example/upstream/commit/abcdef0123456789");
        assertThat(draft.body()).contains("https://github.com/example/origin/commit/feedface");
        assertThat(draft.body()).contains("Unresolved conflicts");
        assertThat(draft.body()).contains("Translation failed");
        assertThat(draft.filesAsBullets()).contains("- docs/guide.md");
    }

    @Test
    void preparesDraftWithoutTranslationCommitFallsBackToPending() {
        PullRequestService service = new PullRequestService(new PullRequestComposer());
        Config config = config();
        GitWorkflowResult workflowResult = workflowResult();

        PullRequestService.PullRequestDraft draft = service.prepareDraft(config,
                workflowResult,
                List.of(),
                Optional.empty(),
                List.of(),
                List.of());

        assertThat(draft.body()).contains("pending (dry-run)");
    }

    private Config config() {
        return new Config(Mode.BATCH,
                URI.create("https://github.com/example/upstream.git"),
                URI.create("https://github.com/example/origin.git"),
                "main",
                "sync-<upstream-short-sha>",
                Optional.empty(),
                true,
                ai.docsite.translator.translate.TranslationMode.DRY_RUN,
                LogFormat.TEXT,
                new TranslatorConfig(LlmProvider.OLLAMA, "model", Optional.of("http://localhost:11434")),
                new Secrets(Optional.of("token"), Optional.empty()),
                Optional.empty(),
                0,
                List.of(),
                Set.of());
    }

    private GitWorkflowResult workflowResult() {
        return new GitWorkflowResult(Path.of("up"), Path.of("origin"),
                "sync-abc1234", "abcdef0123456789", "abc1234", "abc1111", "deadbeef",
                new DiffMetadata(List.of()), MergeStatus.MERGED);
    }
}
