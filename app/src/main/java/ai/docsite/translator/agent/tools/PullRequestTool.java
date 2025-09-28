package ai.docsite.translator.agent.tools;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.pr.PullRequestService.PullRequestDraft;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tool that prepares pull request drafts for review.
 */
public class PullRequestTool {

    private final PullRequestService pullRequestService;
    private final GitWorkflowResult workflowResult;
    private final Config config;

    public PullRequestTool(PullRequestService pullRequestService,
                           GitWorkflowResult workflowResult,
                           Config config) {
        this.pullRequestService = Objects.requireNonNull(pullRequestService, "pullRequestService");
        this.workflowResult = Objects.requireNonNull(workflowResult, "workflowResult");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Tool(name = "preparePullRequestDraft", value = "Prepare a pull request draft for the translation branch")
    public PullRequestDraft preparePullRequestDraft() {
        List<String> files = workflowResult.diffMetadata().changes().stream()
                .map(FileChange::path)
                .collect(Collectors.toList());
        return pullRequestService.prepareDraft(config, workflowResult, files, Optional.empty(), List.of(), List.of());
    }
}
