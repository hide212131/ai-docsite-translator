package ai.docsite.translator.writer;

import java.nio.file.Path;

/**
 * Placeholder writer that will persist translated documents to disk.
 */
public class DocumentWriter {

    public void write(Path source, String content) {
        // Placeholder: real implementation will persist translated content.
        if (source == null || content == null) {
            throw new IllegalArgumentException("Source path and content must be provided");
        }
    }
}
