package ai.docsite.translator.agent.tools;

import ai.docsite.translator.writer.LineStructureAnalysis;
import ai.docsite.translator.writer.LineStructureAnalyzer;
import ai.docsite.translator.writer.LineStructureAdjuster;
import dev.langchain4j.agent.tool.Tool;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tool delegating to the line structure adjuster.
 */
public class LineStructureAdjusterTool {

    private final LineStructureAnalyzer analyzer;
    private final LineStructureAdjuster adjuster;

    public LineStructureAdjusterTool(LineStructureAnalyzer analyzer, LineStructureAdjuster adjuster) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.adjuster = Objects.requireNonNull(adjuster, "adjuster");
    }

    @Tool(name = "adjustLineStructure", value = "Adjust translation output to keep line structure aligned")
    public String adjust(String original, String translated) {
        List<String> originalLines = toLines(original);
        List<String> translatedLines = toLines(translated);
        LineStructureAnalysis analysis = analyzer.analyze(originalLines);
        List<String> adjusted = adjuster.adjust(originalLines, translatedLines, analysis);
        return String.join("\n", adjusted);
    }

    private List<String> toLines(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\\R", -1));
    }
}
