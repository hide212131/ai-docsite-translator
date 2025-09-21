package ai.docsite.translator.cli;

import ai.docsite.translator.agent.AgentOrchestrator;
import ai.docsite.translator.config.Config;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.git.GitWorkflowService;
import ai.docsite.translator.translate.TranslationService;

/**
 * Entry point placeholder wiring the main collaborators together.
 */
public final class CliApplication {

    public static void main(String[] args) {
        Config config = Config.builder().build();
        GitWorkflowService gitWorkflowService = new GitWorkflowService();
        DiffAnalyzer diffAnalyzer = new DiffAnalyzer();
        TranslationService translationService = new TranslationService();
        AgentOrchestrator orchestrator = new AgentOrchestrator(diffAnalyzer, translationService);

        gitWorkflowService.initializeRepositories();
        orchestrator.run();
    }

    private CliApplication() {
        // Prevent instantiation
    }
}
