package ai.docsite.translator.config;

/**
 * Execution mode for the translator CLI.
 */
public enum Mode {
    BATCH,
    DEV;

    public static Mode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BATCH;
        }
        for (Mode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported mode: " + raw);
    }

    public boolean isDev() {
        return this == DEV;
    }
}
