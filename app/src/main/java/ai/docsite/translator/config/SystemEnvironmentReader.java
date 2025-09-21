package ai.docsite.translator.config;

import java.util.Optional;

/**
 * Reads environment variables from the host system.
 */
public class SystemEnvironmentReader implements EnvironmentReader {

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(System.getenv(key));
    }
}
