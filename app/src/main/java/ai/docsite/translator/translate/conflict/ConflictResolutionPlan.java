package ai.docsite.translator.translate.conflict;

import java.util.List;
import java.util.Objects;

/**
 * Holds the cleaned base document and conflict blocks that require translation.
 */
public final class ConflictResolutionPlan {

    private final List<String> baseLines;
    private final List<ConflictBlock> blocks;

    public ConflictResolutionPlan(List<String> baseLines, List<ConflictBlock> blocks) {
        this.baseLines = List.copyOf(Objects.requireNonNull(baseLines, "baseLines"));
        this.blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    public List<String> baseLines() {
        return baseLines;
    }

    public List<ConflictBlock> blocks() {
        return blocks;
    }

    public boolean hasConflicts() {
        return !blocks.isEmpty();
    }
}
