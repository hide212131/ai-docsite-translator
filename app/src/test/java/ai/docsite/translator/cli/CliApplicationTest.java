package ai.docsite.translator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.agent.AgentOrchestrator;
import ai.docsite.translator.config.Config;
import ai.docsite.translator.config.ConfigLoader;
import ai.docsite.translator.config.Mode;
import ai.docsite.translator.config.Secrets;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.git.GitWorkflowService;
import ai.docsite.translator.translate.TranslationService;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CliApplicationTest {

    @Test
    void runCompletesWithSuccessWhenRequiredArgsProvided() {
        Config config = new Config(Mode.BATCH,
                URI.create("https://example.com/up.git"),
                URI.create("https://example.com/origin.git"),
                "main",
                "sync-<upstream-short-sha>",
                Optional.empty(),
                true,
                new Secrets(Optional.empty(), Optional.empty()),
                Optional.empty());

        RecordingGitWorkflowService gitWorkflowService = new RecordingGitWorkflowService();
        CliApplication application = new CliApplication(
                new FixedConfigLoader(config),
                gitWorkflowService,
                new AgentOrchestratorStub());
        int exitCode = application.run(new String[] {
                "--upstream-url", "https://example.com/up.git",
                "--origin-url", "https://example.com/origin.git",
                "--dry-run"
        });

        assertThat(exitCode).isZero();
        assertThat(gitWorkflowService.invocationCount).isEqualTo(1);
    }

    private static final class RecordingGitWorkflowService extends GitWorkflowService {
        private int invocationCount;

        @Override
        public GitWorkflowResult prepareSyncBranch(Config config) {
            invocationCount++;
            return GitWorkflowResult.empty(Path.of("up"), Path.of("origin"));
        }
    }

    private static final class FixedConfigLoader extends ConfigLoader {

        private final Config config;

        FixedConfigLoader(Config config) {
            super(key -> Optional.empty());
            this.config = config;
        }

        @Override
        public Config load(CliArguments arguments) {
            return config;
        }
    }

    private static final class AgentOrchestratorStub extends AgentOrchestrator {
        AgentOrchestratorStub() {
            super(new ai.docsite.translator.diff.DiffAnalyzer(), new TranslationService());
        }

        @Override
        public void run() {
            // no-op for test
        }
    }
}
