package ai.docsite.translator.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Declarative agent contract used by LangChain4j to orchestrate translation workflow decisions.
 */
public interface TranslationAgent {

    @SystemMessage("You are an orchestrator that plans documentation translation tasks. "
            + "Rely on the provided tools to inspect diffs, adjust structure, translate text, and prepare pull request drafts. "
            + "Return a short action summary containing keywords TRANSLATE and/or CREATE_PR when those steps should be performed.")
    String orchestrate(@UserMessage String instructions);
}
