package ai.docsite.translator.cli;

import ai.docsite.translator.agent.AgentOrchestrator;
import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.ConfigLoader;
import ai.docsite.translator.config.SystemEnvironmentReader;
import ai.docsite.translator.diff.DiffAnalyzer;
import ai.docsite.translator.git.GitWorkflowService;
import ai.docsite.translator.translate.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Entry point wiring the command-line parser and configuration loader.
 */
public final class CliApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliApplication.class);

    private final ConfigLoader configLoader;
    private final GitWorkflowService gitWorkflowService;
    private final AgentOrchestrator agentOrchestrator;

    public CliApplication() {
        this(new ConfigLoader(new SystemEnvironmentReader()),
                new GitWorkflowService(),
                new AgentOrchestrator(new DiffAnalyzer(), new TranslationService()));
    }

    CliApplication(ConfigLoader configLoader, GitWorkflowService gitWorkflowService, AgentOrchestrator agentOrchestrator) {
        this.configLoader = configLoader;
        this.gitWorkflowService = gitWorkflowService;
        this.agentOrchestrator = agentOrchestrator;
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
        LOGGER.info("Running in {} mode (dryRun={}): upstream={} origin={}",
                config.mode(), config.dryRun(), config.upstreamUrl(), config.originUrl());

        gitWorkflowService.initializeRepositories();
        agentOrchestrator.run();
        return 0;
    }
}
