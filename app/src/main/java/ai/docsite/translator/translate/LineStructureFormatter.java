package ai.docsite.translator.translate;

import ai.docsite.translator.writer.LineStructureAnalysis;
import ai.docsite.translator.writer.LineStructureAnalyzer;
import ai.docsite.translator.writer.LineStructureAdjuster;
import java.util.List;
import java.util.Objects;

/**
 * Formats translated text to maintain the original document line structure.
 */
public class LineStructureFormatter {

    private final LineStructureAnalyzer analyzer;
    private final LineStructureAdjuster adjuster;

    public LineStructureFormatter(LineStructureAnalyzer analyzer, LineStructureAdjuster adjuster) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.adjuster = Objects.requireNonNull(adjuster, "adjuster");
    }

    public List<String> format(List<String> sourceLines, List<String> translatedLines) {
        LineStructureAnalysis analysis = analyzer.analyze(sourceLines);
        return adjuster.adjust(sourceLines, translatedLines, analysis);
    }
}
