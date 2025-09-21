package ai.docsite.translator.config;

import ai.docsite.translator.cli.CliArguments;
import ai.docsite.translator.translate.TranslationMode;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

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
    static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";
    static final String ENV_TRANSLATION_TARGET_SHA = "TRANSLATION_TARGET_SHA";
    static final String ENV_TRANSLATION_MODE = "TRANSLATION_MODE";

    private static final String DEFAULT_ORIGIN_BRANCH = "main";
    private static final String DEFAULT_BRANCH_TEMPLATE = "sync-<upstream-short-sha>";

    private final EnvironmentReader environmentReader;

    public ConfigLoader(EnvironmentReader environmentReader) {
        this.environmentReader = Objects.requireNonNull(environmentReader, "environmentReader");
    }

    public Config load(CliArguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        Mode mode = resolveMode(arguments);
        boolean dryRun = resolveDryRun(arguments);
        TranslationMode translationMode = resolveTranslationMode(arguments, dryRun);

        URI upstreamUrl = resolveUri(arguments.upstreamUrl(), ENV_UPSTREAM_URL, "upstream repository url must be provided");
        URI originUrl = resolveUri(arguments.originUrl(), ENV_ORIGIN_URL, "origin repository url must be provided");

        String originBranch = firstNonBlank(arguments.originBranch(), ENV_ORIGIN_BRANCH, DEFAULT_ORIGIN_BRANCH);
        String translationBranchTemplate = firstNonBlank(arguments.translationBranchTemplate(), ENV_TRANSLATION_BRANCH_TEMPLATE, DEFAULT_BRANCH_TEMPLATE);

        Optional<String> since = Optional.ofNullable(arguments.since())
                .filter(ConfigLoader::isNotBlank)
                .or(() -> environmentReader.get(ENV_SINCE).filter(ConfigLoader::isNotBlank));
        Optional<String> translationTargetSha = environmentReader.get(ENV_TRANSLATION_TARGET_SHA)
                .filter(ConfigLoader::isNotBlank);

        Optional<String> geminiApiKey = dryRun ? Optional.empty() : environmentReader.get(ENV_GEMINI_API_KEY).filter(ConfigLoader::isNotBlank);
        Optional<String> githubToken = dryRun ? Optional.empty() : environmentReader.get(ENV_GITHUB_TOKEN).filter(ConfigLoader::isNotBlank);

        if (!dryRun) {
            if (geminiApiKey.isEmpty()) {
                throw new IllegalStateException("GEMINI_API_KEY must be provided unless running in dry-run mode");
            }
            if (githubToken.isEmpty()) {
                throw new IllegalStateException("GITHUB_TOKEN must be provided unless running in dry-run mode");
            }
        }

        Secrets secrets = new Secrets(geminiApiKey, githubToken);

        return new Config(mode, upstreamUrl, originUrl, originBranch, translationBranchTemplate, since, dryRun, translationMode, secrets, translationTargetSha);
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
}
