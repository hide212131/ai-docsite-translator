package ai.docsite.translator.translate;

import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import dev.langchain4j.exception.RateLimitException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates translation tasks and ensures the resulting text preserves structural integrity.
 */
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);
    private static final int MAX_TRANSLATION_RETRIES = 3;
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("(?:retry in |retryDelay\"?:\\s*\")([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE);

    private final TranslatorFactory translatorFactory;
    private final LineStructureFormatter formatter;

    public TranslationService() {
        Translator production = new MockTranslator();
        Translator dryRun = new PassThroughTranslator();
        Translator mock = new MockTranslator();
        this.translatorFactory = new TranslatorFactory(production, dryRun, mock);
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
            List<String> rawTranslation = translateWithRetry(translator, sourceSlice);
            List<String> formatted = formatter.format(sourceSlice, rawTranslation);
            boolean emptyOutput = formatted.isEmpty() || formatted.stream().allMatch(String::isBlank);
            if (emptyOutput) {
                formatted = sourceSlice;
            }
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

    private List<String> translateWithRetry(Translator translator, List<String> sourceLines) {
        TranslationException lastFailure = null;
        for (int attempt = 0; attempt < MAX_TRANSLATION_RETRIES; attempt++) {
            try {
                return translator.translate(sourceLines);
            } catch (TranslationException ex) {
                lastFailure = ex;
                Optional<Duration> maybeDelay = retryDelay(ex);
                if (maybeDelay.isEmpty() || attempt == MAX_TRANSLATION_RETRIES - 1) {
                    throw ex;
                }
                Duration delay = maybeDelay.get();
                LOGGER.warn("Translation rate limited; retrying in {} seconds (attempt {}/{})",
                        delay.toSeconds(), attempt + 1, MAX_TRANSLATION_RETRIES);
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw lastFailure == null ? new TranslationException("Unknown translation failure", null) : lastFailure;
    }

    private Optional<Duration> retryDelay(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof RateLimitException) {
                return Optional.of(Duration.ofSeconds(15));
            }
            cause = cause.getCause();
        }

        String message = throwable.getMessage();
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = RETRY_DELAY_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                double seconds = Double.parseDouble(matcher.group(1));
                long millis = Math.max(0, (long) (seconds * 1000));
                return Optional.of(Duration.ofMillis(millis));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (message.contains("RESOURCE_EXHAUSTED") || message.contains("429")) {
            return Optional.of(Duration.ofSeconds(15));
        }
        return Optional.empty();
    }

    public TranslationSummary translateAll(DiffMetadata metadata) {
        int size = metadata == null ? 0 : metadata.changes().size();
        LOGGER.info("Preparing translation jobs for {} files (pipeline stub)", size);
        return new TranslationSummary(size);
    }

    public record TranslationSummary(int processedFiles) { }
}
