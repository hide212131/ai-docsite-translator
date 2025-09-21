package ai.docsite.translator.translate;

import java.util.List;
import java.util.Objects;

/**
 * Result of translating a single document.
 */
public record TranslationResult(String filePath, List<String> lines) {

    public TranslationResult {
        Objects.requireNonNull(filePath, "filePath");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
