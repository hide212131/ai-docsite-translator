package ai.docsite.translator.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds runtime settings for the translation model provider.
 */
public record TranslatorConfig(LlmProvider provider, String modelName, Optional<String> baseUrl) {

    public TranslatorConfig {
        provider = Objects.requireNonNull(provider, "provider");
        modelName = requireNonBlank(modelName, "modelName");
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
    }

    public Optional<String> baseUrl() {
        return baseUrl;
    }

    public boolean isOllama() {
        return provider == LlmProvider.OLLAMA;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
