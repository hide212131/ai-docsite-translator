package ai.docsite.translator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.LlmProvider;
import ai.docsite.translator.config.LogFormat;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.config.TranslatorConfig;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.CommitService;
import ai.docsite.translator.git.CommitService.CommitResult;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.git.GitWorkflowException;
import ai.docsite.translator.pr.PullRequestComposer;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.pr.PullRequestService.PullRequestDraft;
import ai.docsite.translator.translate.TranslationMode;
import ai.docsite.translator.translate.TranslationOutcome;
import ai.docsite.translator.translate.TranslationResult;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTask;
import ai.docsite.translator.translate.TranslationTaskPlanner;
import ai.docsite.translator.translate.conflict.ConflictCleanupService;
import ai.docsite.translator.translate.conflict.ConflictCleanupService.Result;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import ai.docsite.translator.writer.DocumentWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {

    private TranslationServiceSpy translationService;
    private PullRequestServiceSpy pullRequestService;
    private AgentOrchestrator orchestrator;
    private FixedPlanner taskPlanner;
    private RecordingDocumentWriter documentWriter;
    private CommitServiceStub commitService;
    private ConflictCleanupServiceStub conflictCleanupService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationServiceSpy();
        pullRequestService = new PullRequestServiceSpy();
        taskPlanner = new FixedPlanner();
        documentWriter = new RecordingDocumentWriter();
        commitService = new CommitServiceStub();
        conflictCleanupService = new ConflictCleanupServiceStub();
        AgentFactory agentFactory = new AgentFactory(new SimpleRoutingChatModel(), translationService, pullRequestService,
                new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        orchestrator = new AgentOrchestrator(agentFactory, translationService, pullRequestService,
                taskPlanner, documentWriter, commitService, conflictCleanupService);
    }

    @Test
    void batchModeTriggersTranslationAndPullRequestDraft() {
        Config config = config(Mode.BATCH, false);
        GitWorkflowResult workflowResult = workflowResultWithChanges();
        commitService.setCommitResult(new CommitResult(true, true, Optional.of("feedface"),
                "docs: sync-abc1234\n- docs/new.md", List.of("docs/new.md")));
        commitService.setPushShouldSucceed(true);

        AgentRunResult result = orchestrator.run(config, workflowResult);

        assertThat(result.translationTriggered()).isTrue();
        assertThat(result.pullRequestDraftCreated()).isTrue();
        assertThat(result.commitSha()).contains("feedface");
        assertThat(translationService.invocations).isEqualTo(1);
        assertThat(translationService.lastMode).isEqualTo(TranslationMode.PRODUCTION);
        assertThat(pullRequestService.prepareDraftInvocations).isEqualTo(1);
        assertThat(pullRequestService.createInvocations).isEqualTo(1);
        assertThat(pullRequestService.printInvocations).isZero();
        assertThat(documentWriter.invocations).isEqualTo(1);
        assertThat(commitService.invocations).isEqualTo(1);
        assertThat(commitService.pushInvocations).isEqualTo(1);
        assertThat(conflictCleanupService.invocations).isEqualTo(1);
    }

    @Test
    void devModeSkipsTranslationButStillCreatesDraft() {
        Config config = config(Mode.DEV, true);
        GitWorkflowResult workflowResult = workflowResultWithChanges();

        AgentRunResult result = orchestrator.run(config, workflowResult);

        assertThat(result.translationTriggered()).isFalse();
        assertThat(result.pullRequestDraftCreated()).isTrue();
        assertThat(translationService.invocations).isZero();
        assertThat(pullRequestService.prepareDraftInvocations).isEqualTo(1);
        assertThat(pullRequestService.printInvocations).isEqualTo(1);
        assertThat(pullRequestService.createInvocations).isZero();
        assertThat(documentWriter.invocations).isZero();
        assertThat(commitService.invocations).isZero();
        assertThat(commitService.pushInvocations).isZero();
        assertThat(result.commitSha()).isEmpty();
        assertThat(conflictCleanupService.invocations).isZero();
    }

    @Test
    void failsRunWhenPushFails() {
        Config config = config(Mode.BATCH, false);
        GitWorkflowResult workflowResult = workflowResultWithChanges();
        commitService.setCommitResult(new CommitResult(true, true, Optional.of("feedface"),
                "docs: sync-abc1234\n- docs/new.md", List.of("docs/new.md")));
        commitService.setPushShouldSucceed(false);

        assertThatThrownBy(() -> orchestrator.run(config, workflowResult))
                .isInstanceOf(GitWorkflowException.class)
                .hasMessageContaining("push failure");
        assertThat(commitService.pushInvocations).isEqualTo(1);
        assertThat(pullRequestService.createInvocations).isZero();
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
                LogFormat.TEXT,
                new TranslatorConfig(LlmProvider.OLLAMA, "lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0", Optional.of("http://localhost:11434")),
                new Secrets(Optional.empty(), Optional.empty()),
                Optional.empty(),
                0,
                List.of(),
                Set.of(),
                6,
                2,
                60,
                0.3);
    }

    private GitWorkflowResult workflowResultWithChanges() {
        DiffMetadata metadata = new DiffMetadata(List.of(
                new FileChange("docs/new.md", ChangeCategory.DOCUMENT_NEW),
                new FileChange("README.md", ChangeCategory.DOCUMENT_UPDATED)));
        return new GitWorkflowResult(Path.of("up"), Path.of("origin"), "sync-abc1234", "abcdef0123456789", "abc1234", "abc0000", "deadbeef", metadata, MergeStatus.MERGED);
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
        private int prepareDraftInvocations;
        private int printInvocations;
        private int createInvocations;

        PullRequestServiceSpy() {
            super(new PullRequestComposer());
        }

        @Override
        public PullRequestDraft prepareDraft(Config config,
                                             GitWorkflowResult workflowResult,
                                             List<String> files,
                                             Optional<String> translationCommitSha,
                                             List<String> conflictFailures,
                                             List<String> translationFailures) {
            prepareDraftInvocations++;
            return super.prepareDraft(config, workflowResult, files, translationCommitSha, conflictFailures, translationFailures);
        }

        @Override
        public void printDraft(PullRequestDraft draft) {
            printInvocations++;
        }

        @Override
        public Optional<String> createPullRequest(Config config, GitWorkflowResult workflowResult, PullRequestDraft draft) {
            createInvocations++;
            return Optional.of("https://example.com/pr/1");
        }
    }

    private static final class FixedPlanner extends TranslationTaskPlanner {
        private final List<TranslationTask> tasks;

        FixedPlanner() {
            tasks = List.of(new TranslationTask("docs/new.md", List.of("line one"), List.of(), List.of()));
        }

        @Override
        public List<TranslationTask> plan(GitWorkflowResult workflowResult, int maxFilesPerRun) {
            return tasks;
        }

        @Override
        public PlanResult planWithDiagnostics(GitWorkflowResult workflowResult, int maxFilesPerRun) {
            return new PlanResult(tasks, List.of(), List.of());
        }
    }

    private static final class RecordingDocumentWriter extends DocumentWriter {
        private int invocations;

        @Override
        public void write(Path originRoot, TranslationResult result) {
            invocations++;
        }
    }

    private static final class CommitServiceStub extends CommitService {
        private int invocations;
        private CommitResult nonDryRunResult = CommitResult.noChanges();
        private int pushInvocations;
        private boolean pushShouldSucceed;

        void setCommitResult(CommitResult result) {
            this.nonDryRunResult = result;
        }

        void setPushShouldSucceed(boolean value) {
            this.pushShouldSucceed = value;
        }

        @Override
        public CommitResult commitTranslatedFiles(Path repositoryRoot,
                                                  String targetShortSha,
                                                  List<String> translatedFiles,
                                                  boolean dryRun) {
            invocations++;
            if (dryRun) {
                return new CommitResult(!translatedFiles.isEmpty(), false, Optional.empty(),
                        "docs: sync-" + targetShortSha, List.copyOf(translatedFiles));
            }
            return nonDryRunResult;
        }

        @Override
        public void pushTranslationBranch(Path repositoryRoot, String branchName, Optional<String> githubToken) {
            pushInvocations++;
            if (!pushShouldSucceed) {
                throw new GitWorkflowException("simulated push failure");
            }
        }
    }

    private static final class ConflictCleanupServiceStub extends ConflictCleanupService {
        private int invocations;
        private Result result = Result.empty();

        void setResult(Result result) {
            this.result = result;
        }

        @Override
        public Result cleanConflicts(Path repositoryRoot) {
            invocations++;
            return result;
        }
    }
}
