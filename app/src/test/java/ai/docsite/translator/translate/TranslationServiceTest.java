package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import dev.langchain4j.exception.RateLimitException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void retriesOnRateLimitExceptionAndEventuallySucceeds() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator rateLimitedTranslator = lines -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new TranslationException("Rate limited", new RateLimitException("429 Too Many Requests"));
            }
            return List.of("Translated: " + String.join(" ", lines));
        };
        TranslatorFactory factory = new TranslatorFactory(rateLimitedTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 6, 1, 60, 0.1);
        TranslationTask task = new TranslationTask("docs/rate-limited.md",
                List.of("Hello World"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.failedFiles()).isEmpty();
        assertThat(attemptCount.get()).isEqualTo(3);
    }

    @Test
    void failsAfterMaxRetryAttemptsOnRateLimitException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator alwaysRateLimitedTranslator = lines -> {
            attemptCount.incrementAndGet();
            throw new TranslationException("Rate limited", new RateLimitException("429 Too Many Requests"));
        };
        TranslatorFactory factory = new TranslatorFactory(alwaysRateLimitedTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 3, 1, 60, 0.1);
        TranslationTask task = new TranslationTask("docs/always-limited.md",
                List.of("Hello"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.failedFiles()).containsExactly("docs/always-limited.md");
        assertThat(attemptCount.get()).isEqualTo(3);
    }

    @Test
    void retriesOn429ErrorMessage() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator http429Translator = lines -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new TranslationException("HTTP 429: Too Many Requests", null);
            }
            return List.of("Success");
        };
        TranslatorFactory factory = new TranslatorFactory(http429Translator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 5, 1, 60, 0.1);
        TranslationTask task = new TranslationTask("docs/http429.md",
                List.of("Test"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.failedFiles()).isEmpty();
        assertThat(attemptCount.get()).isEqualTo(2);
    }

    @Test
    void retriesOnResourceExhaustedError() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator resourceExhaustedTranslator = lines -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new TranslationException("RESOURCE_EXHAUSTED: Quota exceeded", null);
            }
            return List.of("Success");
        };
        TranslatorFactory factory = new TranslatorFactory(resourceExhaustedTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 5, 1, 60, 0.1);
        TranslationTask task = new TranslationTask("docs/resource-exhausted.md",
                List.of("Test"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.failedFiles()).isEmpty();
        assertThat(attemptCount.get()).isEqualTo(2);
    }

    @Test
    void usesProviderRetryAfterWhenAvailable() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator retryAfterTranslator = lines -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new TranslationException("Rate limited, retry in 2s", new RateLimitException("429"));
            }
            return List.of("Success");
        };
        TranslatorFactory factory = new TranslatorFactory(retryAfterTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 5, 10, 60, 0.0);
        TranslationTask task = new TranslationTask("docs/retry-after.md",
                List.of("Test"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        long startTime = System.currentTimeMillis();
        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.failedFiles()).isEmpty();
        assertThat(attemptCount.get()).isEqualTo(2);
        // Should use 2s from retry-after, not 10s from initialBackoff
        assertThat(duration).isLessThan(5000);
    }

    @Test
    void doesNotRetryOnNonRateLimitErrors() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator otherErrorTranslator = lines -> {
            attemptCount.incrementAndGet();
            throw new TranslationException("Some other error", null);
        };
        TranslatorFactory factory = new TranslatorFactory(otherErrorTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        TranslationService service = new TranslationService(factory, formatter, 5, 1, 60, 0.1);
        TranslationTask task = new TranslationTask("docs/other-error.md",
                List.of("Test"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.failedFiles()).containsExactly("docs/other-error.md");
        assertThat(attemptCount.get()).isEqualTo(1);
    }

    @Test
    void exponentialBackoffIncreasesDelay() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Translator delayMeasuringTranslator = lines -> {
            int attempt = attemptCount.getAndIncrement();
            if (attempt < 2) {
                throw new TranslationException("Rate limited", new RateLimitException("429"));
            }
            return List.of("Success");
        };
        TranslatorFactory factory = new TranslatorFactory(delayMeasuringTranslator, new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        // Use jitter=0 for predictable testing, initialBackoff=1, maxBackoff=10
        TranslationService service = new TranslationService(factory, formatter, 6, 1, 10, 0.0);
        TranslationTask task = new TranslationTask("docs/backoff-test.md",
                List.of("Test"),
                List.of(""),
                List.of(new TranslationSegment(0, 1)));

        long startTime = System.currentTimeMillis();
        TranslationOutcome outcome = service.translate(List.of(task), TranslationMode.PRODUCTION);
        long totalDuration = System.currentTimeMillis() - startTime;

        assertThat(outcome.results()).hasSize(1);
        assertThat(attemptCount.get()).isEqualTo(3);
        // Total duration should be at least 1s (first retry) + 2s (second retry) = 3s
        assertThat(totalDuration).isGreaterThanOrEqualTo(2900);
    }

    @Test
    void validatesRetryConfiguration() {
        TranslatorFactory factory = new TranslatorFactory(new MockTranslator(), new PassThroughTranslator(), new MockTranslator());
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());

        assertThatThrownBy(() -> new TranslationService(factory, formatter, 0, 1, 60, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetryAttempts");

        assertThatThrownBy(() -> new TranslationService(factory, formatter, 6, 0, 60, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialBackoffSeconds");

        assertThatThrownBy(() -> new TranslationService(factory, formatter, 6, 10, 5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBackoffSeconds");

        assertThatThrownBy(() -> new TranslationService(factory, formatter, 6, 1, 60, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitterFactor");

        assertThatThrownBy(() -> new TranslationService(factory, formatter, 6, 1, 60, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitterFactor");
    }
}
