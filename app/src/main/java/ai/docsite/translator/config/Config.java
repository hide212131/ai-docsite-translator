package ai.docsite.translator.config;

import ai.docsite.translator.translate.TranslationMode;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        int maxFilesPerRun,
        List<String> translationIncludePaths,
        Set<String> documentExtensions
) {

    private static final String DEFAULT_TEMPLATE_TOKEN = "<upstream-short-sha>";
    private static final Set<String> DEFAULT_DOCUMENT_EXTENSIONS = Set.of("md", "mdx", "txt", "html");

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
        translationIncludePaths = translationIncludePaths == null
                ? List.of()
                : translationIncludePaths.stream()
                .map(Config::normalizeIncludePath)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableList());
        documentExtensions = documentExtensions == null || documentExtensions.isEmpty()
                ? DEFAULT_DOCUMENT_EXTENSIONS
                : documentExtensions.stream()
                .map(Config::normalizeExtension)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeIncludePath(String raw) {
        String normalized = raw.replace('\\', '/').trim();
        if (normalized.isBlank()) {
            return normalized;
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeExtension(String raw) {
        String normalized = raw.trim();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
