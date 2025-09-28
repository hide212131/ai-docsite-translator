package ai.docsite.translator.agent;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.git.CommitService;
import ai.docsite.translator.git.CommitService.CommitResult;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.pr.PullRequestService.PullRequestDraft;
import ai.docsite.translator.translate.TranslationOutcome;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTask;
import ai.docsite.translator.translate.TranslationTaskPlanner;
import ai.docsite.translator.translate.TranslationTaskPlanner.PlanResult;
import ai.docsite.translator.writer.DocumentWriter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the LangChain4j agent execution and interprets its decisions.
 */
public class AgentOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final AgentFactory agentFactory;
    private final TranslationService translationService;
    private final PullRequestService pullRequestService;
    private final TranslationTaskPlanner taskPlanner;
    private final DocumentWriter documentWriter;
    private final CommitService commitService;

    public AgentOrchestrator(AgentFactory agentFactory,
                             TranslationService translationService,
                             PullRequestService pullRequestService,
                             TranslationTaskPlanner taskPlanner,
                             DocumentWriter documentWriter,
                             CommitService commitService) {
        this.agentFactory = agentFactory;
        this.translationService = translationService;
        this.pullRequestService = pullRequestService;
        this.taskPlanner = taskPlanner;
        this.documentWriter = documentWriter;
        this.commitService = commitService;
    }

    public AgentRunResult run(Config config, GitWorkflowResult workflowResult) {
        if (workflowResult.diffMetadata().changes().isEmpty()) {
            LOGGER.info("No changes detected for agent to process");
            return AgentRunResult.empty();
        }

        TranslationAgent agent = agentFactory.createAgent(config, workflowResult);
        String prompt = AgentPromptFormatter.buildPrompt(config, workflowResult);
        String plan = agent.orchestrate(prompt);
        boolean shouldTranslate = containsKeyword(plan, "TRANSLATE");
        boolean shouldCreatePr = containsKeyword(plan, "CREATE_PR");

        boolean translationTriggered = false;
        boolean pullRequestDraftCreated = false;
        PlanResult planResult = taskPlanner.planWithDiagnostics(workflowResult, config.maxFilesPerRun());
        List<String> conflictFailures = planResult.conflictFiles();
        List<String> translationFailures = List.of();
        CommitResult commitResult = CommitResult.noChanges();
        boolean pushSucceeded = false;

        if (shouldTranslate) {
            List<TranslationTask> tasks = planResult.tasks();
            if (tasks.isEmpty()) {
                LOGGER.info("No document tasks eligible for translation");
            } else {
                TranslationOutcome outcome = translationService.translate(tasks, config.translationMode());
                outcome.results().forEach(result -> documentWriter.write(workflowResult.originDirectory(), result));
                translationTriggered = outcome.processedFiles() > 0;
                translationFailures = outcome.failedFiles();
                if (translationTriggered) {
                    commitResult = commitService.commitTranslatedFiles(
                            workflowResult.originDirectory(),
                            workflowResult.targetCommitShortSha(),
                            outcome.processedFilePaths(),
                            config.dryRun());
                    if (commitResult.committed() && !config.dryRun()) {
                        pushSucceeded = commitService.pushTranslationBranch(
                                workflowResult.originDirectory(),
                                workflowResult.translationBranch(),
                                config.secrets().githubToken());
                        if (!pushSucceeded) {
                            LOGGER.warn("Push failed for branch {}; PR creation will be skipped", workflowResult.translationBranch());
                        }
                    }
                }
            }
        } else {
            LOGGER.info("Agent plan requested skipping translation step");
        }

        if (shouldCreatePr) {
            boolean canCreatePr = config.dryRun() || (commitResult.committed() && pushSucceeded);
            if (canCreatePr) {
                List<String> filesForPr = !commitResult.files().isEmpty()
                        ? commitResult.files()
                        : planResult.plannedFilePaths();
                PullRequestDraft draft = pullRequestService.prepareDraft(
                        config,
                        workflowResult,
                        filesForPr,
                        commitResult.commitSha(),
                        conflictFailures,
                        translationFailures);
                if (config.dryRun()) {
                    pullRequestService.printDraft(draft);
                    pullRequestDraftCreated = true;
                } else {
                    pullRequestService.createPullRequest(config, workflowResult, draft);
                    pullRequestDraftCreated = true;
                }
            } else {
                LOGGER.info("Skipping PR creation because no commit was produced or push failed");
            }
        }

        return new AgentRunResult(plan,
                translationTriggered,
                pullRequestDraftCreated,
                commitResult.commitSha(),
                conflictFailures,
                translationFailures);
    }

    private boolean containsKeyword(String plan, String keyword) {
        return plan != null && plan.toUpperCase().contains(keyword);
    }
}
