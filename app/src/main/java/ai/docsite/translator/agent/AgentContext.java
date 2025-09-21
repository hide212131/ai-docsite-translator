package ai.docsite.translator.agent;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.git.GitWorkflowResult;
import java.util.Objects;

/**
 * Aggregated context passed to agent executions.
 */
public record AgentContext(Config config, GitWorkflowResult workflowResult) {

    public AgentContext {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(workflowResult, "workflowResult");
    }
}
