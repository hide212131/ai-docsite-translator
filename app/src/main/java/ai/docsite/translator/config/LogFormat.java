package ai.docsite.translator.config;

/**
 * Supported log output formats.
 */
public enum LogFormat {
    TEXT,
    JSON;

    public static LogFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Log format must be provided");
        }
        return LogFormat.valueOf(raw.trim().toUpperCase());
    }
}
