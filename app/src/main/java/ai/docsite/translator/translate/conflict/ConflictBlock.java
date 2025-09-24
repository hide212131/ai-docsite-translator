package ai.docsite.translator.translate.conflict;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single merge conflict block within a document.
 */
public final class ConflictBlock {

    private final int startLine;
    private final List<String> baseLines;
    private final List<String> incomingLines;

    public ConflictBlock(int startLine, List<String> baseLines, List<String> incomingLines) {
        this.startLine = startLine;
        this.baseLines = List.copyOf(Objects.requireNonNull(baseLines, "baseLines"));
        this.incomingLines = List.copyOf(Objects.requireNonNull(incomingLines, "incomingLines"));
    }

    public int startLine() {
        return startLine;
    }

    public List<String> baseLines() {
        return baseLines;
    }

    public List<String> incomingLines() {
        return incomingLines;
    }

    public int length() {
        return baseLines.size();
    }
}
