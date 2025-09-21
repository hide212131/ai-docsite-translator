package ai.docsite.translator.agent.tools;

import ai.docsite.translator.writer.LineStructureAdjuster;
import dev.langchain4j.agent.tool.Tool;
import java.util.Objects;

/**
 * Tool delegating to the line structure adjuster.
 */
public class LineStructureAdjusterTool {

    private final LineStructureAdjuster adjuster;

    public LineStructureAdjusterTool(LineStructureAdjuster adjuster) {
        this.adjuster = Objects.requireNonNull(adjuster, "adjuster");
    }

    @Tool(name = "adjustLineStructure", value = "Adjust translation output to keep line structure aligned")
    public String adjust(String original, String translated) {
        return adjuster.adjust(original, translated);
    }
}
