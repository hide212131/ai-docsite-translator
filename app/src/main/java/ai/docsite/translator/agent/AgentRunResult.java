package ai.docsite.translator.agent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome returned after agent orchestration.
 */
public record AgentRunResult(String planSummary,
                             boolean translationTriggered,
                             boolean pullRequestDraftCreated,
                             Optional<String> commitSha,
                             List<String> conflictFailures,
                             List<String> translationFailures) {

    public AgentRunResult {
        planSummary = Objects.requireNonNullElse(planSummary, "");
        commitSha = commitSha == null ? Optional.empty() : commitSha;
        conflictFailures = List.copyOf(conflictFailures == null ? List.of() : conflictFailures);
        translationFailures = List.copyOf(translationFailures == null ? List.of() : translationFailures);
    }

    public static AgentRunResult empty() {
        return new AgentRunResult("", false, false, Optional.empty(), List.of(), List.of());
    }
}
