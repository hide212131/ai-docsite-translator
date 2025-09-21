package ai.docsite.translator.agent;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.translate.TranslationService;
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

    public AgentOrchestrator(AgentFactory agentFactory,
                             TranslationService translationService,
                             PullRequestService pullRequestService) {
        this.agentFactory = agentFactory;
        this.translationService = translationService;
        this.pullRequestService = pullRequestService;
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

        if (shouldTranslate) {
            translationService.translateAll(workflowResult.diffMetadata());
            translationTriggered = true;
        } else {
            LOGGER.info("Agent plan requested skipping translation step");
        }

        if (shouldCreatePr && config.dryRun()) {
            pullRequestService.prepareDryRunDraft(workflowResult);
            pullRequestDraftCreated = true;
        }

        return new AgentRunResult(plan, translationTriggered, pullRequestDraftCreated);
    }

    private boolean containsKeyword(String plan, String keyword) {
        return plan != null && plan.toUpperCase().contains(keyword);
    }
}
