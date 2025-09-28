package ai.docsite.translator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.docsite.translator.cli.CliArguments;
import ai.docsite.translator.translate.TranslationMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ConfigLoaderTest {

    @Test
    void assemblesConfigFromCliArguments() {
        CliArguments cliArguments = CommandLine.populateCommand(new CliArguments(),
                "--mode", "dev",
                "--upstream-url", "https://github.com/example/upstream.git",
                "--origin-url", "https://github.com/example/origin.git",
                "--origin-branch", "docs",
                "--translation-branch-template", "custom-sync-<upstream-short-sha>",
                "--since", "abc123",
                "--dry-run",
                "--limit", "5");

        Config config = new ConfigLoader(key -> Optional.empty()).load(cliArguments);

        assertThat(config.mode()).isEqualTo(Mode.DEV);
        assertThat(config.upstreamUrl()).isEqualTo(URI.create("https://github.com/example/upstream.git"));
        assertThat(config.originBranch()).isEqualTo("docs");
        assertThat(config.translationBranchTemplate()).isEqualTo("custom-sync-<upstream-short-sha>");
        assertThat(config.since()).contains("abc123");
        assertThat(config.dryRun()).isTrue();
        assertThat(config.translationMode()).isEqualTo(TranslationMode.DRY_RUN);
        assertThat(config.translatorConfig().provider()).isEqualTo(LlmProvider.OLLAMA);
        assertThat(config.translatorConfig().modelName()).isEqualTo("lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0");
        assertThat(config.translatorConfig().baseUrl()).contains("http://localhost:11434");
        assertThat(config.secrets().githubToken()).isEmpty();
        assertThat(config.secrets().geminiApiKey()).isEmpty();
        assertThat(config.maxFilesPerRun()).isEqualTo(5);
        assertThat(config.translationIncludePaths()).isEmpty();
        assertThat(config.documentExtensions()).containsExactlyInAnyOrder("md", "mdx", "txt", "html");
    }

    @Test
    void fallsBackToEnvironmentValuesWhenCliOmitted() {
        Map<String, String> envValues = new HashMap<>();
        envValues.put(ConfigLoader.ENV_UPSTREAM_URL, "https://example.com/up.git");
        envValues.put(ConfigLoader.ENV_ORIGIN_URL, "https://example.com/origin.git");
        envValues.put(ConfigLoader.ENV_ORIGIN_BRANCH, "release");
        envValues.put(ConfigLoader.ENV_TRANSLATION_BRANCH_TEMPLATE, "sync-<upstream-short-sha>");
        envValues.put(ConfigLoader.ENV_OLLAMA_BASE_URL, "http://ollama:11434");
        envValues.put(ConfigLoader.ENV_LLM_MODEL, "custom-gguf");
        envValues.put(ConfigLoader.ENV_GITHUB_TOKEN, "github-token");
        envValues.put(ConfigLoader.ENV_MAX_FILES_PER_RUN, "2");
        envValues.put(ConfigLoader.ENV_TRANSLATION_INCLUDE_PATHS, "docs,docs/releases");
        envValues.put(ConfigLoader.ENV_DOCUMENT_EXTENSIONS, ".md,.mdx");

        RecordingEnvironmentReader environmentReader = new RecordingEnvironmentReader(envValues);
        CliArguments cliArguments = CommandLine.populateCommand(new CliArguments());

        Config config = new ConfigLoader(environmentReader).load(cliArguments);

        assertThat(config.mode()).isEqualTo(Mode.BATCH);
        assertThat(config.upstreamUrl()).isEqualTo(URI.create("https://example.com/up.git"));
        assertThat(config.originBranch()).isEqualTo("release");
        assertThat(config.translationBranchTemplate()).isEqualTo("sync-<upstream-short-sha>");
        assertThat(config.translationMode()).isEqualTo(TranslationMode.PRODUCTION);
        assertThat(config.translatorConfig().baseUrl()).contains("http://ollama:11434");
        assertThat(config.translatorConfig().modelName()).isEqualTo("custom-gguf");
        assertThat(config.secrets().githubToken()).contains("github-token");
        assertThat(config.secrets().geminiApiKey()).isEmpty();
        assertThat(config.maxFilesPerRun()).isEqualTo(2);
        assertThat(config.translationIncludePaths()).containsExactly("docs", "docs/releases");
        assertThat(config.documentExtensions()).containsExactlyInAnyOrder("md", "mdx");
    }

    @Test
    void dryRunSkipsSecretAccess() {
        RecordingEnvironmentReader environmentReader = new RecordingEnvironmentReader(Map.of());
        CliArguments cliArguments = CommandLine.populateCommand(new CliArguments(),
                "--upstream-url", "https://example.com/up.git",
                "--origin-url", "https://example.com/origin.git",
                "--dry-run");

        Config config = new ConfigLoader(environmentReader).load(cliArguments);

        assertThat(config.dryRun()).isTrue();
        assertThat(config.translationMode()).isEqualTo(TranslationMode.DRY_RUN);
        assertThat(config.secrets().githubToken()).isEmpty();
    }

    @Test
    void missingSecretsInBatchModeThrows() {
        RecordingEnvironmentReader environmentReader = new RecordingEnvironmentReader(Map.of(
                ConfigLoader.ENV_UPSTREAM_URL, "https://example.com/up.git",
                ConfigLoader.ENV_ORIGIN_URL, "https://example.com/origin.git"));
        CliArguments cliArguments = CommandLine.populateCommand(new CliArguments());

        Throwable thrown = catchThrowable(() -> new ConfigLoader(environmentReader).load(cliArguments));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_TOKEN");
    }

    @Test
    void sinceInBatchModeCausesValidationError() {
        RecordingEnvironmentReader environmentReader = new RecordingEnvironmentReader(Map.of(
                ConfigLoader.ENV_UPSTREAM_URL, "https://example.com/up.git",
                ConfigLoader.ENV_ORIGIN_URL, "https://example.com/origin.git",
                ConfigLoader.ENV_GITHUB_TOKEN, "github"));
        CliArguments cliArguments = CommandLine.populateCommand(new CliArguments(),
                "--since", "abc123");

        Throwable thrown = catchThrowable(() -> new ConfigLoader(environmentReader).load(cliArguments));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dev mode");
    }

    private static final class RecordingEnvironmentReader implements EnvironmentReader {

        private final Map<String, String> values;
        private final List<String> requestedKeys = new ArrayList<>();

        private RecordingEnvironmentReader(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<String> get(String key) {
            requestedKeys.add(key);
            return Optional.ofNullable(values.get(key));
        }

        List<String> requestedKeys() {
            return requestedKeys;
        }
    }
}
