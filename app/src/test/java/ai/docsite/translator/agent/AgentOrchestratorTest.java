package ai.docsite.translator.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestComposer;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.pr.PullRequestService.PullRequestDraft;
import ai.docsite.translator.translate.TranslationMode;
import ai.docsite.translator.translate.TranslationOutcome;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTask;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {

    private TranslationServiceSpy translationService;
    private PullRequestServiceSpy pullRequestService;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        translationService = new TranslationServiceSpy();
        pullRequestService = new PullRequestServiceSpy();
        AgentFactory agentFactory = new AgentFactory(new SimpleRoutingChatModel(), translationService, pullRequestService,
                new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        orchestrator = new AgentOrchestrator(agentFactory, translationService, pullRequestService);
    }

    @Test
    void batchModeTriggersTranslationAndPullRequestDraft() {
        Config config = config(Mode.BATCH, false);
        GitWorkflowResult workflowResult = workflowResultWithChanges();

        AgentRunResult result = orchestrator.run(config, workflowResult);

        assertThat(result.translationTriggered()).isTrue();
        assertThat(result.pullRequestDraftCreated()).isFalse();
        assertThat(translationService.invocations).isEqualTo(1);
        assertThat(translationService.lastMode).isEqualTo(TranslationMode.PRODUCTION);
        assertThat(pullRequestService.invocations).isZero();
    }

    @Test
    void devModeSkipsTranslationButStillCreatesDraft() {
        Config config = config(Mode.DEV, true);
        GitWorkflowResult workflowResult = workflowResultWithChanges();

        AgentRunResult result = orchestrator.run(config, workflowResult);

        assertThat(result.translationTriggered()).isFalse();
        assertThat(result.pullRequestDraftCreated()).isTrue();
        assertThat(translationService.invocations).isZero();
        assertThat(pullRequestService.invocations).isEqualTo(1);
    }

    private Config config(Mode mode, boolean dryRun) {
        return new Config(mode,
                URI.create("https://example.com/up.git"),
                URI.create("https://example.com/origin.git"),
                "main",
                "sync-<upstream-short-sha>",
                Optional.empty(),
                dryRun,
                dryRun ? TranslationMode.DRY_RUN : TranslationMode.PRODUCTION,
                new Secrets(Optional.empty(), Optional.empty()),
                Optional.empty());
    }

    private GitWorkflowResult workflowResultWithChanges() {
        DiffMetadata metadata = new DiffMetadata(List.of(
                new FileChange("docs/new.md", ChangeCategory.DOCUMENT_NEW),
                new FileChange("README.md", ChangeCategory.DOCUMENT_UPDATED)));
        return new GitWorkflowResult(Path.of("up"), Path.of("origin"), "sync-abc1234", "abcdef0123456789", "abc1234", metadata, MergeStatus.MERGED);
    }

    private static final class TranslationServiceSpy extends TranslationService {
        private int invocations;
        private TranslationMode lastMode;

        @Override
        public TranslationOutcome translate(List<TranslationTask> tasks, TranslationMode mode) {
            invocations++;
            lastMode = mode;
            return super.translate(tasks, mode);
        }
    }

    private static final class PullRequestServiceSpy extends PullRequestService {
        private int invocations;

        PullRequestServiceSpy() {
            super(new PullRequestComposer());
        }

        @Override
        public PullRequestDraft prepareDryRunDraft(GitWorkflowResult workflowResult) {
            invocations++;
            return super.prepareDryRunDraft(workflowResult);
        }
    }
}
