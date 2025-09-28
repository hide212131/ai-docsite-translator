package ai.docsite.translator.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate of translation results for a batch run.
 */
public record TranslationOutcome(List<TranslationResult> results,
                                 List<String> failedFiles) {

    public TranslationOutcome {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        failedFiles = List.copyOf(Objects.requireNonNull(failedFiles, "failedFiles"));
    }

    public int processedFiles() {
        return results.size();
    }

    public List<String> processedFilePaths() {
        if (results.isEmpty()) {
            return List.of();
        }
        List<String> files = new ArrayList<>(results.size());
        for (TranslationResult result : results) {
            files.add(result.filePath());
        }
        return files;
    }
}
