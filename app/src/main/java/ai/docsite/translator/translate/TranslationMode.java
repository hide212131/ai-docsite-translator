package ai.docsite.translator.translate;

/**
 * Mode controlling how translations are executed.
 */
public enum TranslationMode {
    PRODUCTION,
    DRY_RUN,
    MOCK;

    public static TranslationMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PRODUCTION;
        }
        for (TranslationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported translation mode: " + raw);
    }
}
