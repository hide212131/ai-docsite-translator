package ai.docsite.translator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Declarative agent contract used by LangChain4j to orchestrate translation workflow decisions.
 */
public interface TranslationAgent {

    @SystemMessage("You are an orchestrator that plans documentation translation tasks. "
            + "Rely on available tools to inspect diffs, adjust structure, translate text, and prepare pull request drafts. "
            + "Respond with a concise plan including the keywords TRANSLATE and/or CREATE_PR when those actions are required.")
    @UserMessage("""
            {{context}}
            """)
    @Agent(name = "translation-supervisor", description = "Decides what translation workflow actions to run", outputName = "plan")
    String orchestrate(@V("context") String context);
}
