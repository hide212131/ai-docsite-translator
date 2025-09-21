package ai.docsite.translator.writer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aligns translation lines with the original line structure by inserting placeholder lines where necessary.
 */
public class DefaultLineStructureAdjuster implements LineStructureAdjuster {

    @Override
    public List<String> adjust(List<String> originalLines, List<String> translatedLines, LineStructureAnalysis analysis) {
        if (analysis == null || analysis.totalLines() == 0) {
            return translatedLines == null ? Collections.emptyList() : new ArrayList<>(translatedLines);
        }

        List<String> source = originalLines == null ? Collections.nCopies(analysis.totalLines(), "") : originalLines;
        List<String> translation = translatedLines == null ? Collections.emptyList() : translatedLines;

        List<String> adjusted = new ArrayList<>(Collections.nCopies(analysis.totalLines(), ""));
        int translationCursor = 0;

        for (LineSegment segment : analysis.segments()) {
            for (int i = 0; i < segment.length(); i++) {
                int position = segment.startIndex() + i;
                switch (segment.type()) {
                    case CONTENT -> {
                        String value = translationCursor < translation.size() ? translation.get(translationCursor) : "";
                        adjusted.set(position, value);
                        if (translationCursor < translation.size()) {
                            translationCursor++;
                        }
                    }
                    case WHITESPACE -> {
                        String value = position < source.size() ? source.get(position) : "";
                        adjusted.set(position, value.isEmpty() ? " " : value);
                    }
                    case EMPTY -> adjusted.set(position, "");
                }
            }
        }

        return adjusted;
    }
}
