package ai.docsite.translator.config;

/**
 * Supported large language model providers.
 */
public enum LlmProvider {
    GEMINI,
    OLLAMA;

    public static LlmProvider from(String value) {
        if (value == null) {
            return OLLAMA;
        }
        return switch (value.trim().toLowerCase()) {
            case "gemini" -> GEMINI;
            case "ollama", "" -> OLLAMA;
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + value);
        };
    }
}
