package ai.docsite.translator.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds sensitive credentials needed for external integrations.
 */
public record Secrets(Optional<String> githubToken, Optional<String> geminiApiKey) {

    public Secrets {
        githubToken = githubToken == null ? Optional.empty() : githubToken;
        geminiApiKey = geminiApiKey == null ? Optional.empty() : geminiApiKey;
    }

    public boolean isProductionReady() {
        return githubToken.filter(s -> !s.isBlank()).isPresent();
    }

    public Secrets sanitizeForDryRun() {
        return new Secrets(Optional.empty(), geminiApiKey);
    }
}
