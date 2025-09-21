package ai.docsite.translator.translate;

import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates translation tasks and ensures the resulting text preserves structural integrity.
 */
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);

    private final TranslatorFactory translatorFactory;
    private final LineStructureFormatter formatter;

    public TranslationService() {
        Translator placeholder = new MockTranslator();
        this.translatorFactory = new TranslatorFactory(placeholder, placeholder, placeholder);
        this.formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
    }

    public TranslationService(TranslatorFactory translatorFactory, LineStructureFormatter formatter) {
        this.translatorFactory = Objects.requireNonNull(translatorFactory, "translatorFactory");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public TranslationOutcome translate(List<TranslationTask> tasks, TranslationMode mode) {
        if (tasks == null || tasks.isEmpty()) {
            return new TranslationOutcome(List.of());
        }
        Translator translator = translatorFactory.select(mode);
        List<TranslationResult> results = new ArrayList<>();
        for (TranslationTask task : tasks) {
            results.add(translateTask(task, translator));
        }
        return new TranslationOutcome(results);
    }

    public TranslationResult translateTask(TranslationTask task, TranslationMode mode) {
        return translateTask(task, translatorFactory.select(mode));
    }

    private TranslationResult translateTask(TranslationTask task, Translator translator) {
        List<String> translated = new ArrayList<>(task.existingTranslationLines());
        ensureCapacity(translated, task.sourceLines().size());

        List<TranslationSegment> segments = new ArrayList<>(task.segments());
        segments.sort(Comparator.comparingInt(TranslationSegment::startLine));

        for (TranslationSegment segment : segments) {
            List<String> sourceSlice = new ArrayList<>(task.sourceLines().subList(segment.startLine(), segment.endLineExclusive()));
            List<String> rawTranslation = translator.translate(sourceSlice);
            List<String> formatted = formatter.format(sourceSlice, rawTranslation);
            replaceRange(translated, segment.startLine(), segment.endLineExclusive(), formatted);
        }

        ensureCapacity(translated, task.sourceLines().size());
        if (translated.size() > task.sourceLines().size()) {
            translated = new ArrayList<>(translated.subList(0, task.sourceLines().size()));
        }
        return new TranslationResult(task.filePath(), List.copyOf(translated));
    }

    private void ensureCapacity(List<String> target, int size) {
        while (target.size() < size) {
            target.add("");
        }
    }

    private void replaceRange(List<String> target, int start, int end, List<String> replacement) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(end, target.size());
        for (int i = safeStart; i < safeEnd; i++) {
            target.remove(safeStart);
        }
        target.addAll(safeStart, replacement);
    }

    public TranslationSummary translateAll(DiffMetadata metadata) {
        int size = metadata == null ? 0 : metadata.changes().size();
        LOGGER.info("Preparing translation jobs for {} files (pipeline stub)", size);
        return new TranslationSummary(size);
    }

    public record TranslationSummary(int processedFiles) { }
}
