package ai.docsite.translator.agent;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.git.GitWorkflowResult;
import java.util.EnumMap;
import java.util.Map;

final class AgentPromptFormatter {

    private AgentPromptFormatter() {
    }

    static String buildPrompt(Config config, GitWorkflowResult workflowResult) {
        Map<ChangeCategory, Integer> counts = new EnumMap<>(ChangeCategory.class);
        for (ChangeCategory category : ChangeCategory.values()) {
            counts.put(category, workflowResult.diffMetadata().byCategory(category).size());
        }

        return "Mode=" + config.mode() + "\n"
                + "DryRun=" + config.dryRun() + "\n"
                + "TranslationTarget=" + workflowResult.targetCommitShortSha() + "\n"
                + "Changes=" + counts + "\n"
                + "Provide TRANSLATE if translation should run and CREATE_PR to prepare PR.";
    }
}
