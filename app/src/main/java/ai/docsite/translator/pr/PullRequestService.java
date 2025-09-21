package ai.docsite.translator.pr;

import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.GitWorkflowResult;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service preparing pull request artifacts.
 */
public class PullRequestService {

    private final PullRequestComposer composer;

    public PullRequestService(PullRequestComposer composer) {
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    public PullRequestDraft prepareDryRunDraft(GitWorkflowResult workflowResult) {
        Objects.requireNonNull(workflowResult, "workflowResult");
        List<String> files = workflowResult.diffMetadata().changes().stream()
                .map(FileChange::path)
                .collect(Collectors.toList());
        String title = composer.composeTitle(workflowResult.targetCommitShortSha());
        String body = composer.composeBody(files);
        return new PullRequestDraft(title, body, files);
    }

    public record PullRequestDraft(String title, String body, List<String> files) { }
}
