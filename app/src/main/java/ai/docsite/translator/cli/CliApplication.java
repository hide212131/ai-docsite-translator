package ai.docsite.translator.cli;

import ai.docsite.translator.agent.AgentFactory;
import ai.docsite.translator.agent.AgentOrchestrator;
import ai.docsite.translator.agent.AgentRunResult;
import ai.docsite.translator.agent.SimpleRoutingChatModel;
import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.ConfigLoader;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.config.SystemEnvironmentReader;
import ai.docsite.translator.config.TranslatorConfig;
import ai.docsite.translator.git.CommitService;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.git.GitWorkflowService;
import ai.docsite.translator.logging.LoggingConfigurator;
import ai.docsite.translator.pr.PullRequestComposer;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.translate.ChatModelTranslator;
import ai.docsite.translator.translate.LineStructureFormatter;
import ai.docsite.translator.translate.MockTranslator;
import ai.docsite.translator.translate.PassThroughTranslator;
import ai.docsite.translator.translate.TranslationMode;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationTaskPlanner;
import ai.docsite.translator.translate.Translator;
import ai.docsite.translator.translate.TranslatorFactory;
import ai.docsite.translator.translate.conflict.ConflictCleanupService;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import ai.docsite.translator.writer.DocumentWriter;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.time.Duration;

/**
 * Entry point wiring the command-line parser and configuration loader.
 */
public final class CliApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliApplication.class);

    private final ConfigLoader configLoader;
    private final GitWorkflowService gitWorkflowService;

    public CliApplication() {
        this(new ConfigLoader(new SystemEnvironmentReader()), new GitWorkflowService());
    }

    CliApplication(ConfigLoader configLoader, GitWorkflowService gitWorkflowService) {
        this.configLoader = configLoader;
        this.gitWorkflowService = gitWorkflowService;
    }

    public static void main(String[] args) {
        new CliApplication().run(args);
    }

    public int run(String[] args) {
        CliArguments cliArguments = new CliArguments();
        CommandLine commandLine = new CommandLine(cliArguments);

        try {
            commandLine.parseArgs(args);
        } catch (CommandLine.ParameterException ex) {
            commandLine.getErr().println(ex.getMessage());
            commandLine.usage(commandLine.getErr());
            return commandLine.getCommandSpec().exitCodeOnInvalidInput();
        }

        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(commandLine.getOut());
            return commandLine.getCommandSpec().exitCodeOnUsageHelp();
        }

        Config config = configLoader.load(cliArguments);
        LoggingConfigurator.configure(config.logFormat());
        LOGGER.info("Running in {} mode (dryRun={}): upstream={} origin={}",
                config.mode(), config.dryRun(), config.upstreamUrl(), config.originUrl());

        dev.langchain4j.model.chat.ChatModel chatModel = createChatModel(config);
        TranslationService translationService = createTranslationService(config, chatModel);
        PullRequestService pullRequestService = new PullRequestService(new PullRequestComposer());
        TranslationTaskPlanner taskPlanner = new TranslationTaskPlanner(chatModel, config.translationMode());
        DocumentWriter documentWriter = new DocumentWriter();
        CommitService commitService = new CommitService();
        ConflictCleanupService conflictCleanupService = new ConflictCleanupService(translationService, config.translationMode());
        AgentFactory agentFactory = new AgentFactory(new SimpleRoutingChatModel(), translationService, pullRequestService,
                new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        AgentOrchestrator agentOrchestrator = new AgentOrchestrator(agentFactory, translationService, pullRequestService,
                taskPlanner, documentWriter, commitService, conflictCleanupService);

        GitWorkflowResult workflowResult = gitWorkflowService.prepareSyncBranch(config);
        if (!workflowResult.translationBranch().isEmpty()) {
            LOGGER.info("Prepared translation branch {} targeting {}", workflowResult.translationBranch(), workflowResult.targetCommitShortSha());
        } else {
            LOGGER.info("Repositories already synchronized with upstream");
        }
        AgentRunResult runResult = agentOrchestrator.run(config, workflowResult);
        LOGGER.info("Agent plan: {}", runResult.planSummary());
        if (!runResult.conflictFailures().isEmpty()) {
            LOGGER.warn("Auto-resolution skipped for conflicted files: {}", String.join(", ", runResult.conflictFailures()));
        }
        if (!runResult.translationFailures().isEmpty()) {
            LOGGER.warn("Translation failed for files: {}", String.join(", ", runResult.translationFailures()));
        }
        runResult.commitSha().ifPresent(sha -> LOGGER.info("Translation commit: {}", sha));
        return 0;
    }

    private dev.langchain4j.model.chat.ChatModel createChatModel(Config config) {
        TranslatorConfig translatorConfig = config.translatorConfig();
        return switch (translatorConfig.provider()) {
            case OLLAMA -> createOllamaChatModel(translatorConfig);
            case GEMINI -> createGeminiChatModel(translatorConfig, config.secrets());
        };
    }

    private TranslationService createTranslationService(Config config, dev.langchain4j.model.chat.ChatModel chatModel) {
        TranslatorFactory factory = buildTranslatorFactory(chatModel, config);
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        return new TranslationService(factory, formatter,
                config.llmMaxRetryAttempts(),
                config.llmInitialBackoffSeconds(),
                config.llmMaxBackoffSeconds(),
                config.llmRetryJitterFactor(),
                config.maxFilesPerRun());
    }

    private TranslatorFactory buildTranslatorFactory(dev.langchain4j.model.chat.ChatModel chatModel, Config config) {
        Translator productionTranslator = createProductionTranslator(chatModel, config);
        Translator dryRunTranslator = new PassThroughTranslator();
        Translator mockTranslator = new MockTranslator();
        return new TranslatorFactory(productionTranslator, dryRunTranslator, mockTranslator);
    }

    private Translator createProductionTranslator(dev.langchain4j.model.chat.ChatModel chatModel, Config config) {
        TranslatorConfig translatorConfig = config.translatorConfig();
        return new ChatModelTranslator(chatModel, translatorConfig.provider().name(), translatorConfig.modelName());
    }

    private dev.langchain4j.model.chat.ChatModel createOllamaChatModel(TranslatorConfig translatorConfig) {
        try {
            String baseUrl = translatorConfig.baseUrl()
                    .orElseThrow(() -> new IllegalStateException("OLLAMA_BASE_URL must be configured when LLM_PROVIDER=ollama"));
            LOGGER.info("Using Ollama model '{}' via {}", translatorConfig.modelName(), baseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(translatorConfig.modelName())
                    .temperature(0.1)
                    .timeout(Duration.ofMinutes(2))
                    .build();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to initialize Ollama chat model", ex);
        }
    }

    private dev.langchain4j.model.chat.ChatModel createGeminiChatModel(TranslatorConfig translatorConfig, Secrets secrets) {
        String apiKey = secrets.geminiApiKey()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("GEMINI_API_KEY must be provided when LLM_PROVIDER=gemini"));
        try {
            LOGGER.info("Using Gemini model '{}'", translatorConfig.modelName());
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(translatorConfig.modelName())
                    .temperature(0.1)
                    .timeout(Duration.ofMinutes(2))
                    .build();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to initialize Gemini chat model", ex);
        }
    }
}
