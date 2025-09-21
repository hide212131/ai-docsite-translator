package ai.docsite.translator.config;

import java.util.Objects;

/**
 * Placeholder configuration object that will aggregate CLI args and environment settings.
 */
public final class Config {
    private final String mode;

    private Config(Builder builder) {
        this.mode = builder.mode;
    }

    public String mode() {
        return mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String mode = "dev";

        public Builder mode(String mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}
