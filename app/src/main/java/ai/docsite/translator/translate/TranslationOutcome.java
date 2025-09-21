package ai.docsite.translator.translate;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate of translation results for a batch run.
 */
public record TranslationOutcome(List<TranslationResult> results) {

    public TranslationOutcome {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }

    public int processedFiles() {
        return results.size();
    }
}
