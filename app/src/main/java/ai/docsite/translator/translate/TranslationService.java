package ai.docsite.translator.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder translation service that will integrate Gemini via LangChain4j.
 */
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);

    public void translateAll() {
        LOGGER.info("Translating pending documents (placeholder)");
    }
}
