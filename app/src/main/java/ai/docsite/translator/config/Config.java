package ai.docsite.translator.config;

import ai.docsite.translator.translate.TranslationMode;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of the runtime configuration assembled from CLI arguments and environment values.
 */
public record Config(
        Mode mode,
        URI upstreamUrl,
        URI originUrl,
        String originBranch,
        String translationBranchTemplate,
        Optional<String> since,
        boolean dryRun,
        TranslationMode translationMode,
        TranslatorConfig translatorConfig,
        Secrets secrets,
        Optional<String> translationTargetSha,
        int maxFilesPerRun
) {

    private static final String DEFAULT_TEMPLATE_TOKEN = "<upstream-short-sha>";

    public Config {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(upstreamUrl, "upstreamUrl");
        Objects.requireNonNull(originUrl, "originUrl");
        originBranch = requireNonBlank(originBranch, "originBranch");
        translationBranchTemplate = requireNonBlank(translationBranchTemplate, "translationBranchTemplate");
        if (!translationBranchTemplate.contains(DEFAULT_TEMPLATE_TOKEN)) {
            throw new IllegalArgumentException("translationBranchTemplate must contain " + DEFAULT_TEMPLATE_TOKEN);
        }
        since = since == null ? Optional.empty() : since;
        translationMode = Objects.requireNonNull(translationMode, "translationMode");
        translatorConfig = Objects.requireNonNull(translatorConfig, "translatorConfig");
        secrets = Objects.requireNonNull(secrets, "secrets");
        translationTargetSha = translationTargetSha == null ? Optional.empty() : translationTargetSha;
        if (maxFilesPerRun < 0) {
            throw new IllegalArgumentException("maxFilesPerRun must be greater than or equal to zero");
        }
        if (mode == Mode.BATCH && since.isPresent()) {
            throw new IllegalArgumentException("--since can only be used in dev mode");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
