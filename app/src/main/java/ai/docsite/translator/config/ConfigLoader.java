package ai.docsite.translator.config;

import ai.docsite.translator.cli.CliArguments;
import ai.docsite.translator.translate.TranslationMode;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a {@link Config} instance by combining CLI arguments with environment variables and defaults.
 */
public class ConfigLoader {

    static final String ENV_UPSTREAM_URL = "UPSTREAM_URL";
    static final String ENV_ORIGIN_URL = "ORIGIN_URL";
    static final String ENV_ORIGIN_BRANCH = "ORIGIN_BRANCH";
    static final String ENV_MODE = "MODE";
    static final String ENV_TRANSLATION_BRANCH_TEMPLATE = "TRANSLATION_BRANCH_TEMPLATE";
    static final String ENV_SINCE = "SINCE";
    static final String ENV_DRY_RUN = "DRY_RUN";
    static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";
    static final String ENV_TRANSLATION_TARGET_SHA = "TRANSLATION_TARGET_SHA";
    static final String ENV_TRANSLATION_MODE = "TRANSLATION_MODE";
    static final String ENV_MAX_FILES_PER_RUN = "MAX_FILES_PER_RUN";
    static final String ENV_OLLAMA_BASE_URL = "OLLAMA_BASE_URL";
    static final String ENV_LLM_PROVIDER = "LLM_PROVIDER";
    static final String ENV_LLM_MODEL = "LLM_MODEL";
    static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    static final String ENV_TRANSLATION_INCLUDE_PATHS = "TRANSLATION_INCLUDE_PATHS";
    static final String ENV_DOCUMENT_EXTENSIONS = "TRANSLATION_DOCUMENT_EXTENSIONS";
    static final String ENV_LOG_FORMAT = "LOG_FORMAT";
    static final String ENV_LLM_MAX_RETRY_ATTEMPTS = "LLM_MAX_RETRY_ATTEMPTS";
    static final String ENV_LLM_INITIAL_BACKOFF_SECONDS = "LLM_INITIAL_BACKOFF_SECONDS";
    static final String ENV_LLM_MAX_BACKOFF_SECONDS = "LLM_MAX_BACKOFF_SECONDS";
    static final String ENV_LLM_RETRY_JITTER_FACTOR = "LLM_RETRY_JITTER_FACTOR";

    private static final String DEFAULT_ORIGIN_BRANCH = "main";
    private static final String DEFAULT_BRANCH_TEMPLATE = "sync-<upstream-short-sha>";
    private static final Set<String> DEFAULT_DOCUMENT_EXTENSIONS = Set.of("md", "mdx", "txt", "html");
    private static final int DEFAULT_LLM_MAX_RETRY_ATTEMPTS = 6;
    private static final int DEFAULT_LLM_INITIAL_BACKOFF_SECONDS = 2;
    private static final int DEFAULT_LLM_MAX_BACKOFF_SECONDS = 60;
    private static final double DEFAULT_LLM_RETRY_JITTER_FACTOR = 0.3;

    private final EnvironmentReader environmentReader;

    public ConfigLoader(EnvironmentReader environmentReader) {
        this.environmentReader = Objects.requireNonNull(environmentReader, "environmentReader");
    }

    public Config load(CliArguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        Mode mode = resolveMode(arguments);
        boolean dryRun = resolveDryRun(arguments);
        TranslationMode translationMode = resolveTranslationMode(arguments, dryRun);
        LogFormat logFormat = resolveLogFormat(arguments);

        URI upstreamUrl = resolveUri(arguments.upstreamUrl(), ENV_UPSTREAM_URL, "upstream repository url must be provided");
        URI originUrl = resolveUri(arguments.originUrl(), ENV_ORIGIN_URL, "origin repository url must be provided");

        String originBranch = firstNonBlank(arguments.originBranch(), ENV_ORIGIN_BRANCH, DEFAULT_ORIGIN_BRANCH);
        String translationBranchTemplate = firstNonBlank(arguments.translationBranchTemplate(), ENV_TRANSLATION_BRANCH_TEMPLATE, DEFAULT_BRANCH_TEMPLATE);

        Optional<String> since = Optional.ofNullable(arguments.since())
                .filter(ConfigLoader::isNotBlank)
                .or(() -> environmentReader.get(ENV_SINCE).filter(ConfigLoader::isNotBlank));
        Optional<String> translationTargetSha = environmentReader.get(ENV_TRANSLATION_TARGET_SHA)
                .filter(ConfigLoader::isNotBlank);

        Optional<String> githubToken = environmentReader.get(ENV_GITHUB_TOKEN).filter(ConfigLoader::isNotBlank);
        Optional<String> geminiApiKey = environmentReader.get(ENV_GEMINI_API_KEY).filter(ConfigLoader::isNotBlank);

        LlmProvider provider = environmentReader.get(ENV_LLM_PROVIDER)
                .filter(ConfigLoader::isNotBlank)
                .map(LlmProvider::from)
                .orElse(LlmProvider.OLLAMA);

        String modelName = environmentReader.get(ENV_LLM_MODEL)
                .filter(ConfigLoader::isNotBlank)
                .orElse(defaultModelFor(provider));

        Optional<String> baseUrl = Optional.empty();
        if (provider == LlmProvider.OLLAMA) {
            String value = environmentReader.get(ENV_OLLAMA_BASE_URL)
                    .filter(ConfigLoader::isNotBlank)
                    .orElse("http://localhost:11434");
            baseUrl = Optional.of(value);
        }

        int maxFilesPerRun = resolveMaxFilesPerRun(arguments);
        if (maxFilesPerRun == -1) {
            maxFilesPerRun = environmentReader.get(ENV_MAX_FILES_PER_RUN)
                    .filter(ConfigLoader::isNotBlank)
                    .map(String::trim)
                    .map(ConfigLoader::parsePositiveInteger)
                    .orElse(0);
        }

        List<String> includePaths = environmentReader.get(ENV_TRANSLATION_INCLUDE_PATHS)
                .filter(ConfigLoader::isNotBlank)
                .map(ConfigLoader::parseIncludePaths)
                .orElse(List.of());

        Set<String> documentExtensions = environmentReader.get(ENV_DOCUMENT_EXTENSIONS)
                .filter(ConfigLoader::isNotBlank)
                .map(ConfigLoader::parseDocumentExtensions)
                .orElse(DEFAULT_DOCUMENT_EXTENSIONS);

        int llmMaxRetryAttempts = environmentReader.get(ENV_LLM_MAX_RETRY_ATTEMPTS)
                .filter(ConfigLoader::isNotBlank)
                .map(String::trim)
                .map(ConfigLoader::parsePositiveInteger)
                .orElse(DEFAULT_LLM_MAX_RETRY_ATTEMPTS);

        int llmInitialBackoffSeconds = environmentReader.get(ENV_LLM_INITIAL_BACKOFF_SECONDS)
                .filter(ConfigLoader::isNotBlank)
                .map(String::trim)
                .map(ConfigLoader::parsePositiveInteger)
                .orElse(DEFAULT_LLM_INITIAL_BACKOFF_SECONDS);

        int llmMaxBackoffSeconds = environmentReader.get(ENV_LLM_MAX_BACKOFF_SECONDS)
                .filter(ConfigLoader::isNotBlank)
                .map(String::trim)
                .map(ConfigLoader::parsePositiveInteger)
                .orElse(DEFAULT_LLM_MAX_BACKOFF_SECONDS);

        double llmRetryJitterFactor = environmentReader.get(ENV_LLM_RETRY_JITTER_FACTOR)
                .filter(ConfigLoader::isNotBlank)
                .map(String::trim)
                .map(ConfigLoader::parseDouble)
                .orElse(DEFAULT_LLM_RETRY_JITTER_FACTOR);

        if (!dryRun && githubToken.isEmpty()) {
            throw new IllegalStateException("GITHUB_TOKEN must be provided unless running in dry-run mode");
        }

        Secrets secrets = new Secrets(githubToken, geminiApiKey);
        TranslatorConfig translatorConfig = new TranslatorConfig(provider, modelName, baseUrl);

        return new Config(mode, upstreamUrl, originUrl, originBranch, translationBranchTemplate, since, dryRun,
                translationMode, logFormat, translatorConfig, secrets, translationTargetSha, maxFilesPerRun, includePaths, documentExtensions,
                llmMaxRetryAttempts, llmInitialBackoffSeconds, llmMaxBackoffSeconds, llmRetryJitterFactor);
    }

    private String defaultModelFor(LlmProvider provider) {
        return switch (provider) {
            case GEMINI -> "models/gemini-1.5-pro-latest";
            case OLLAMA -> "lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0";
        };
    }

    private Mode resolveMode(CliArguments arguments) {
        Mode cliMode = arguments.mode();
        if (cliMode != null) {
            return cliMode;
        }
        return environmentReader.get(ENV_MODE)
                .map(Mode::from)
                .orElse(Mode.BATCH);
    }

    private boolean resolveDryRun(CliArguments arguments) {
        if (arguments.dryRun()) {
            return true;
        }
        return environmentReader.get(ENV_DRY_RUN)
                .map(String::trim)
                .map(value -> value.equalsIgnoreCase("true") || value.equals("1"))
                .orElse(false);
    }

    private TranslationMode resolveTranslationMode(CliArguments arguments, boolean dryRun) {
        TranslationMode cliMode = arguments.translationMode();
        if (cliMode != null) {
            return cliMode;
        }
        return environmentReader.get(ENV_TRANSLATION_MODE)
                .map(TranslationMode::from)
                .orElse(dryRun ? TranslationMode.DRY_RUN : TranslationMode.PRODUCTION);
    }

    private LogFormat resolveLogFormat(CliArguments arguments) {
        LogFormat cliFormat = arguments.logFormat();
        if (cliFormat != null) {
            return cliFormat;
        }
        return environmentReader.get(ENV_LOG_FORMAT)
                .filter(ConfigLoader::isNotBlank)
                .map(LogFormat::from)
                .orElse(LogFormat.TEXT);
    }

    private int resolveMaxFilesPerRun(CliArguments arguments) {
        Integer limit = arguments.translationLimit();
        if (limit == null) {
            return -1;
        }
        if (limit < 0) {
            throw new IllegalArgumentException("--limit must be zero or greater");
        }
        return limit;
    }

    private static int parsePositiveInteger(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                throw new IllegalArgumentException("MAX_FILES_PER_RUN must be zero or greater");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MAX_FILES_PER_RUN must be an integer", ex);
        }
    }

    private URI resolveUri(URI cliValue, String envKey, String errorMessage) {
        if (cliValue != null) {
            return cliValue;
        }
        return environmentReader.get(envKey)
                .filter(ConfigLoader::isNotBlank)
                .map(URI::create)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    private String firstNonBlank(String cliValue, String envKey, String defaultValue) {
        if (isNotBlank(cliValue)) {
            return cliValue;
        }
        return environmentReader.get(envKey)
                .filter(ConfigLoader::isNotBlank)
                .orElse(defaultValue);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> parseIncludePaths(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(ConfigLoader::isNotBlank)
                .map(value -> value.replace('\\', '/'))
                .collect(Collectors.toList());
    }

    private static Set<String> parseDocumentExtensions(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(ConfigLoader::isNotBlank)
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid double value: " + raw, ex);
        }
    }
}
