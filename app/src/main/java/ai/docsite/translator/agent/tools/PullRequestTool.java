package ai.docsite.translator.agent.tools;

import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.pr.PullRequestService.PullRequestDraft;
import dev.langchain4j.agent.tool.Tool;
import java.util.Objects;

/**
 * Tool that prepares pull request drafts for review.
 */
public class PullRequestTool {

    private final PullRequestService pullRequestService;
    private final GitWorkflowResult workflowResult;

    public PullRequestTool(PullRequestService pullRequestService, GitWorkflowResult workflowResult) {
        this.pullRequestService = Objects.requireNonNull(pullRequestService, "pullRequestService");
        this.workflowResult = Objects.requireNonNull(workflowResult, "workflowResult");
    }

    @Tool(name = "preparePullRequestDraft", value = "Prepare a pull request draft for the translation branch")
    public PullRequestDraft preparePullRequestDraft() {
        return pullRequestService.prepareDryRunDraft(workflowResult);
    }
}
