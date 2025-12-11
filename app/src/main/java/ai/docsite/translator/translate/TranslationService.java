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
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("(?:retry in |retryDelay\"?:\\s*\")([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE);

    private final TranslatorFactory translatorFactory;
    private final LineStructureFormatter formatter;
    private final int maxRetryAttempts;
    private final int initialBackoffSeconds;
    private final int maxBackoffSeconds;
    private final double jitterFactor;

    public TranslationService() {
        Translator production = new MockTranslator();
        Translator dryRun = new PassThroughTranslator();
        Translator mock = new MockTranslator();
        this.translatorFactory = new TranslatorFactory(production, dryRun, mock);
        this.formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        this.maxRetryAttempts = 6;
        this.initialBackoffSeconds = 2;
        this.maxBackoffSeconds = 60;
        this.jitterFactor = 0.3;
    }

    public TranslationService(TranslatorFactory translatorFactory, LineStructureFormatter formatter) {
        this(translatorFactory, formatter, 6, 2, 60, 0.3);
    }

    public TranslationService(TranslatorFactory translatorFactory, LineStructureFormatter formatter,
                              int maxRetryAttempts, int initialBackoffSeconds, int maxBackoffSeconds, double jitterFactor) {
        this.translatorFactory = Objects.requireNonNull(translatorFactory, "translatorFactory");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        if (maxRetryAttempts < 1) {
            throw new IllegalArgumentException("maxRetryAttempts must be at least 1");
        }
        if (initialBackoffSeconds < 1) {
            throw new IllegalArgumentException("initialBackoffSeconds must be at least 1");
        }
        if (maxBackoffSeconds < initialBackoffSeconds) {
            throw new IllegalArgumentException("maxBackoffSeconds must be at least initialBackoffSeconds");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.jitterFactor = jitterFactor;
    }

    public TranslationOutcome translate(List<TranslationTask> tasks, TranslationMode mode) {
        if (tasks == null || tasks.isEmpty()) {
            return new TranslationOutcome(List.of(), List.of());
        }
        Translator translator = translatorFactory.select(mode);
        List<TranslationResult> results = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        for (TranslationTask task : tasks) {
            try {
                results.add(translateTask(task, translator));
            } catch (TranslationException ex) {
                LOGGER.error("Translation failed for {}: {}", task.filePath(), ex.getMessage(), ex);
                failedFiles.add(task.filePath());
            }
        }
        return new TranslationOutcome(results, failedFiles);
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
            LOGGER.info("Translating {} lines {}-{}", task.filePath(), segment.startLine(), segment.endLineExclusive());
            List<String> sourceSlice = new ArrayList<>(task.sourceLines().subList(segment.startLine(), segment.endLineExclusive()));
            List<String> rawTranslation = translateWithRetry(translator, sourceSlice);
            LOGGER.info("Translator returned {} lines for {} segment {}-{}", rawTranslation.size(), task.filePath(), segment.startLine(), segment.endLineExclusive());
            if (!rawTranslation.isEmpty()) {
            LOGGER.debug("Translation output for {} segment {}-{}:\n{}", task.filePath(), segment.startLine(), segment.endLineExclusive(), String.join("\n", rawTranslation));
            }
            List<String> formatted;
            if (rawTranslation.size() == sourceSlice.size()) {
                formatted = new ArrayList<>(rawTranslation);
            } else {
                formatted = formatter.format(sourceSlice, rawTranslation);
                if (formatted.size() != sourceSlice.size()) {
                    LOGGER.warn("Formatted output line count {} does not match source {} for {} segment {}-{}; falling back to normalized translation", formatted.size(), sourceSlice.size(), task.filePath(), segment.startLine(), segment.endLineExclusive());
                    formatted = normalizeTranslation(rawTranslation, sourceSlice.size());
                }
            }
            LOGGER.debug("Formatted output for {} segment {}-{}:\n{}", task.filePath(), segment.startLine(), segment.endLineExclusive(), String.join("\n", formatted));
            boolean emptyOutput = formatted.isEmpty() || formatted.stream().allMatch(String::isBlank);
            if (emptyOutput) {
                LOGGER.warn("Received blank translation for {} segment {}-{}; falling back to source", task.filePath(), segment.startLine(), segment.endLineExclusive());
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

    private List<String> normalizeTranslation(List<String> translation, int expectedSize) {
        List<String> normalized = new ArrayList<>(expectedSize);
        if (translation == null) {
            translation = List.of();
        }
        for (int i = 0; i < expectedSize; i++) {
            if (i < translation.size()) {
                normalized.add(translation.get(i));
            } else {
                normalized.add("");
            }
        }
        return normalized;
    }

    private List<String> translateWithRetry(Translator translator, List<String> sourceLines) {
        TranslationException lastFailure = null;
        for (int attempt = 0; attempt < maxRetryAttempts; attempt++) {
            try {
                return translator.translate(sourceLines);
            } catch (TranslationException ex) {
                lastFailure = ex;
                Optional<Duration> maybeDelay = calculateRetryDelay(ex, attempt);
                if (maybeDelay.isEmpty() || attempt == maxRetryAttempts - 1) {
                    if (isRateLimitError(ex)) {
                        LOGGER.error("Translation rate limited; max retries ({}) exceeded", maxRetryAttempts);
                    }
                    throw ex;
                }
                Duration delay = maybeDelay.get();
                LOGGER.warn("Translation rate limited (429/RESOURCE_EXHAUSTED); retrying in {} seconds (attempt {}/{})",
                        delay.toSeconds(), attempt + 1, maxRetryAttempts);
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Translation retry interrupted");
                    throw ex;
                }
            }
        }
        throw lastFailure == null ? new TranslationException("Unknown translation failure", null) : lastFailure;
    }

    private Optional<Duration> calculateRetryDelay(Throwable throwable, int attemptNumber) {
        if (!isRateLimitError(throwable)) {
            return Optional.empty();
        }

        // First check if provider returned a retry-after value
        Optional<Duration> providerDelay = extractProviderRetryAfter(throwable);
        if (providerDelay.isPresent()) {
            return providerDelay;
        }

        // Calculate exponential backoff: initialBackoff * 2^attemptNumber
        // For attemptNumber 0,1,2,3... this produces delays of 2s, 4s, 8s, 16s... (with initialBackoff=2)
        long baseDelaySeconds = initialBackoffSeconds * (1L << attemptNumber);
        
        // Cap at maxBackoff
        long cappedDelaySeconds = Math.min(baseDelaySeconds, maxBackoffSeconds);
        
        // Apply jitter: delay * (1 ± jitterFactor) to avoid thundering herd
        double jitterMultiplier = 1.0 + (Math.random() * 2.0 - 1.0) * jitterFactor;
        long finalDelaySeconds = Math.max(1, (long) (cappedDelaySeconds * jitterMultiplier));
        
        return Optional.of(Duration.ofSeconds(finalDelaySeconds));
    }

    private boolean isRateLimitError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof RateLimitException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && (message.contains("RESOURCE_EXHAUSTED") || message.contains("429"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Optional<Duration> extractProviderRetryAfter(Throwable throwable) {
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
        return Optional.empty();
    }

    public TranslationSummary translateAll(DiffMetadata metadata) {
        int size = metadata == null ? 0 : metadata.changes().size();
        LOGGER.info("Preparing translation jobs for {} files (pipeline stub)", size);
        return new TranslationSummary(size);
    }

    public record TranslationSummary(int processedFiles) { }
}
