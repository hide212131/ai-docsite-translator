package ai.docsite.translator.writer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.translate.TranslationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesTranslatedLinesAndCreatesDirectories() throws Exception {
        DocumentWriter writer = new DocumentWriter();
        TranslationResult result = new TranslationResult("docs/sample.md", List.of("行1", "行2"));

        writer.write(tempDir, result);

        Path written = tempDir.resolve("docs/sample.md");
        assertThat(Files.exists(written)).isTrue();
        String content = Files.readString(written, StandardCharsets.UTF_8);
        String expected = String.join(System.lineSeparator(), result.lines()) + System.lineSeparator();
        assertThat(content).isEqualTo(expected);
    }
}
