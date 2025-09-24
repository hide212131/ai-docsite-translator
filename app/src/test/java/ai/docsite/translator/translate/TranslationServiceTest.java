package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationServiceTest {

    private final Translator uppercaseTranslator = source -> source.stream().map(String::toUpperCase).toList();
    private final LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());

    @Test
    void alignsNewDocumentToSourceStructure() {
        TranslationService service = new TranslationService(new TranslatorFactory(uppercaseTranslator, uppercaseTranslator, uppercaseTranslator), formatter);
        TranslationTask task = new TranslationTask("docs/new.md",
                List.of("first line", "", "second line"),
                List.of(),
                List.of());

        TranslationResult result = service.translateTask(task, TranslationMode.PRODUCTION);

        assertThat(result.lines()).containsExactly("FIRST LINE", "", "SECOND LINE");
    }

    @Test
    void replacesOnlySpecifiedSegments() {
        TranslationService service = new TranslationService(new TranslatorFactory(uppercaseTranslator, uppercaseTranslator, uppercaseTranslator), formatter);
        TranslationTask task = new TranslationTask("docs/update.md",
                List.of("", "updated", "", "keep"),
                List.of("", "旧訳", "", "keep"),
                List.of(new TranslationSegment(1, 2)));

        TranslationResult result = service.translateTask(task, TranslationMode.PRODUCTION);

        assertThat(result.lines()).containsExactly("", "UPDATED", "", "keep");
    }

    @Test
    void selectsTranslatorBasedOnMode() {
        Translator mock = source -> source.stream().map(line -> "MOCK:" + line).toList();
        Translator dryRun = source -> source.stream().map(line -> "DRY:" + line).toList();
        Translator prod = source -> source.stream().map(line -> "PROD:" + line).toList();
        TranslationService service = new TranslationService(new TranslatorFactory(prod, dryRun, mock), formatter);
        TranslationTask task = new TranslationTask("file.txt", List.of("line"), List.of(), List.of());

        assertThat(service.translateTask(task, TranslationMode.MOCK).lines()).containsExactly("MOCK:line");
        assertThat(service.translateTask(task, TranslationMode.DRY_RUN).lines()).containsExactly("DRY:line");
        assertThat(service.translateTask(task, TranslationMode.PRODUCTION).lines()).containsExactly("PROD:line");
    }

    @Test
    void fallsBackToSourceWhenTranslatorReturnsBlankOutput() {
        Translator blankTranslator = source -> source.stream().map(line -> " ").toList();
        TranslationService service = new TranslationService(new TranslatorFactory(blankTranslator, blankTranslator, blankTranslator), formatter);
        TranslationTask task = new TranslationTask("docs/addition.md",
                List.of("new content", "more"),
                List.of("", ""),
                List.of(new TranslationSegment(0, 2)));

        TranslationResult result = service.translateTask(task, TranslationMode.PRODUCTION);

        assertThat(result.lines()).containsExactly("new content", "more");
    }
}
