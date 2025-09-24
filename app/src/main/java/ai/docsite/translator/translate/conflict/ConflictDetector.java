package ai.docsite.translator.translate.conflict;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses files containing Git merge conflict markers and prepares translation plans.
 */
public final class ConflictDetector {

    private static final String MARKER_START = "<<<<<<<";
    private static final String MARKER_MID = "=======";
    private static final String MARKER_END = ">>>>>>>";

    public Optional<ConflictResolutionPlan> detect(List<String> fileLines) {
        if (fileLines == null || fileLines.isEmpty()) {
            return Optional.empty();
        }

        List<String> baseLines = new ArrayList<>();
        List<ConflictBlock> blocks = new ArrayList<>();

        boolean inConflict = false;
        boolean readingHead = false;
        boolean readingIncoming = false;
        List<String> headBuffer = new ArrayList<>();
        List<String> incomingBuffer = new ArrayList<>();
        int blockStart = -1;

        for (String line : fileLines) {
            if (!inConflict && line.startsWith(MARKER_START)) {
                inConflict = true;
                readingHead = true;
                headBuffer = new ArrayList<>();
                incomingBuffer = new ArrayList<>();
                blockStart = baseLines.size();
                continue;
            }
            if (inConflict && line.startsWith(MARKER_MID)) {
                readingHead = false;
                readingIncoming = true;
                continue;
            }
            if (inConflict && line.startsWith(MARKER_END)) {
                finalizeBlock(baseLines, blocks, blockStart, headBuffer, incomingBuffer);
                inConflict = false;
                readingIncoming = false;
                blockStart = -1;
                continue;
            }

            if (inConflict) {
                if (readingHead) {
                    headBuffer.add(line);
                } else if (readingIncoming) {
                    incomingBuffer.add(line);
                }
            } else {
                baseLines.add(line);
            }
        }

        if (!blocks.isEmpty()) {
            return Optional.of(new ConflictResolutionPlan(baseLines, blocks));
        }
        return Optional.empty();
    }

    private void finalizeBlock(List<String> baseLines,
                               List<ConflictBlock> blocks,
                               int start,
                               List<String> headLines,
                               List<String> incomingLines) {
        int targetSize = incomingLines.size();
        List<String> segment = new ArrayList<>(targetSize);
        for (int i = 0; i < targetSize; i++) {
            segment.add(i < headLines.size() ? headLines.get(i) : "");
        }
        // If the incoming side removed lines, segment stays empty and no translation is required.
        baseLines.addAll(segment);
        blocks.add(new ConflictBlock(start, segment, incomingLines));
    }
}
