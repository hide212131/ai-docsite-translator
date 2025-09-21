package ai.docsite.translator.agent;

import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.translate.TranslationService;

/**
 * Placeholder orchestrator that will coordinate LangChain4j agents.
 */
public class AgentOrchestrator {

    private final DiffAnalyzer diffAnalyzer;
    private final TranslationService translationService;

    public AgentOrchestrator(DiffAnalyzer diffAnalyzer, TranslationService translationService) {
        this.diffAnalyzer = diffAnalyzer;
        this.translationService = translationService;
    }

    public void run() {
        diffAnalyzer.analyze();
        translationService.translateAll();
    }
}
