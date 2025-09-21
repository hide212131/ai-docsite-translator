package ai.docsite.translator.translate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single document translation task comprised of one or more segments.
 */
public class TranslationTask {

    private final String filePath;
    private final List<String> sourceLines;
    private final List<String> existingTranslationLines;
    private final List<TranslationSegment> segments;

    public TranslationTask(String filePath,
                           List<String> sourceLines,
                           List<String> existingTranslationLines,
                           List<TranslationSegment> segments) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.sourceLines = List.copyOf(Objects.requireNonNull(sourceLines, "sourceLines"));
        this.existingTranslationLines = existingTranslationLines == null
                ? Collections.emptyList()
                : List.copyOf(existingTranslationLines);
        this.segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public String filePath() {
        return filePath;
    }

    public List<String> sourceLines() {
        return sourceLines;
    }

    public List<String> existingTranslationLines() {
        return existingTranslationLines;
    }

    public List<TranslationSegment> segments() {
        if (segments.isEmpty()) {
            return List.of(new TranslationSegment(0, sourceLines.size()));
        }
        return segments;
    }
}
