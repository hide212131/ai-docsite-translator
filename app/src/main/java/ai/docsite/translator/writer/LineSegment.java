package ai.docsite.translator.writer;

import java.util.Objects;

/**
 * Represents a contiguous block of lines of a certain structural type.
 */
public record LineSegment(LineType type, int startIndex, int endIndexExclusive) {

    public LineSegment {
        Objects.requireNonNull(type, "type");
        if (startIndex < 0 || endIndexExclusive < startIndex) {
            throw new IllegalArgumentException("Invalid line segment boundaries");
        }
    }

    public int length() {
        return endIndexExclusive - startIndex;
    }
}
