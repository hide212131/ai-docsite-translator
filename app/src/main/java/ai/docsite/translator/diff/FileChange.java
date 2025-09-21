package ai.docsite.translator.diff;

import java.util.Objects;

/**
 * Represents a single file change and its classified category.
 */
public record FileChange(String path, ChangeCategory category) {

    public FileChange {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(category, "category");
    }
}
