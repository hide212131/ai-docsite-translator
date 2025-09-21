package ai.docsite.translator.writer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation grouping consecutive lines by structural type.
 */
public class DefaultLineStructureAnalyzer implements LineStructureAnalyzer {

    @Override
    public LineStructureAnalysis analyze(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new LineStructureAnalysis(0, Collections.emptyList());
        }

        List<LineSegment> segments = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            LineType type = classify(lines.get(index));
            int start = index;
            index++;
            while (index < lines.size() && classify(lines.get(index)) == type) {
                index++;
            }
            segments.add(new LineSegment(type, start, index));
        }

        return new LineStructureAnalysis(lines.size(), segments);
    }

    private LineType classify(String line) {
        if (line == null || line.isEmpty()) {
            return LineType.EMPTY;
        }
        if (line.trim().isEmpty()) {
            return LineType.WHITESPACE;
        }
        return LineType.CONTENT;
    }
}
