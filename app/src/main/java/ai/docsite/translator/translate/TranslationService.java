package ai.docsite.translator.translate;

import ai.docsite.translator.diff.DiffMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder translation service that will integrate Gemini via LangChain4j.
 */
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);

    public TranslationSummary translateAll(DiffMetadata metadata) {
        LOGGER.info("Translating {} files (placeholder)", metadata.changes().size());
        return new TranslationSummary(metadata.changes().size());
    }

    public record TranslationSummary(int processedFiles) { }
}
