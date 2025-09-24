package ai.docsite.translator.writer;

import ai.docsite.translator.translate.TranslationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes translated documents back into the origin repository workspace.
 */
public class DocumentWriter {

    public void write(Path originRoot, TranslationResult result) {
        if (originRoot == null || result == null) {
            throw new IllegalArgumentException("originRoot and result must be provided");
        }
        Path target = originRoot.resolve(result.filePath());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, result.lines(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write translated document: " + target, ex);
        }
    }
}
