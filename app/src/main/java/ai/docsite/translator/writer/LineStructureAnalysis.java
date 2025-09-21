package ai.docsite.translator.writer;

import java.util.List;
import java.util.Objects;

/**
 * Structural metadata describing contiguous line segments grouped by type.
 */
public record LineStructureAnalysis(int totalLines, List<LineSegment> segments) {

    public LineStructureAnalysis {
        Objects.requireNonNull(segments, "segments");
    }
}
