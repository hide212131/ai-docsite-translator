package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationServiceTest {

    @Test
    void recordsFailedFilesWhenTranslationThrows() {
        Translator failingTranslator = lines -> { throw new TranslationException("boom", null); };
        TranslatorFactory factory = new TranslatorFactory(failingTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter);
        TranslationTask task = new TranslationTask("docs/error.md",
                List.of("Hello"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.failedFiles()).containsExactly("docs/error.md");
    }
}
