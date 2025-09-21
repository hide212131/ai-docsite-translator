package ai.docsite.translator.agent;

import java.util.Objects;

/**
 * Outcome returned after agent orchestration.
 */
public record AgentRunResult(String planSummary, boolean translationTriggered, boolean pullRequestDraftCreated) {

    public AgentRunResult {
        planSummary = Objects.requireNonNullElse(planSummary, "");
    }

    public static AgentRunResult empty() {
        return new AgentRunResult("", false, false);
    }
}
