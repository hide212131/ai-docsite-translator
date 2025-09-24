package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiTranslatorIntegrationTest {

    @Test
    @DisplayName("Calls Gemini API via LangChain4j and reports detailed failures")
    void translateSingleLine() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY is required for this integration test");

        GeminiTranslator translator = new GeminiTranslator(apiKey.trim());
        List<String> source = List.of("Hello from integration test.");

        try {
            List<String> translated = translator.translate(source);
            assertThat(translated)
                    .as("Gemini translation result")
                    .isNotEmpty();
        } catch (TranslationException ex) {
            fail("Gemini translation failed: " + ex.getMessage(), ex);
        }
    }
}
