package ai.docsite.translator.config;

import java.util.Optional;

@FunctionalInterface
public interface EnvironmentReader {
    Optional<String> get(String key);
}
