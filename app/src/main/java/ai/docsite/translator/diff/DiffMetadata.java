package ai.docsite.translator.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregate of classified file changes the downstream translation flow consumes.
 */
public class DiffMetadata {

    private final List<FileChange> changes;

    public DiffMetadata(List<FileChange> changes) {
        this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
    }

    public static DiffMetadata empty() {
        return new DiffMetadata(List.of());
    }

    public List<FileChange> changes() {
        return changes;
    }

    public List<FileChange> byCategory(ChangeCategory category) {
        return changes.stream()
                .filter(change -> change.category() == category)
                .collect(Collectors.toList());
    }
}
