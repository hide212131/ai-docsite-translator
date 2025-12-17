package ai.docsite.translator.translate;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for TranslationDecisionService with real Gemini API.
 * Only runs when GEMINI_API_KEY environment variable is set.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class TranslationDecisionServiceIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationDecisionServiceIntegrationTest.class);

    @Test
    void testCommit9c8b17fTypoFixIsSkipped() {
        // Get API key from environment
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY must be set").isNotBlank();

        // Create Gemini chat model - trying stable model first
        String modelName = "gemini-1.5-flash"; // More stable than experimental models
        var chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
                .build();
        
        LOGGER.info("Created Gemini chat model '{}' with API key length: {}", modelName, apiKey.length());

        TranslationDecisionService service = new TranslationDecisionService(chatModel);

        // Test Case 1: Typo fix from commit 9c8b17f - "availabel" -> "available"
        LOGGER.info("Testing typo fix: availabel -> available");
        List<String> before1 = List.of(
            "angular.module('yourApp')",
            "  .controller('BootswatchController', function ($scope, BootSwatchService) {",
            "    /*Get the list of availabel bootswatch themes*/",
            "    BootSwatchService.get().then(function(themes) {",
            "      $scope.themes = themes;",
            "    });",
            "  });"
        );
        List<String> after1 = List.of(
            "angular.module('yourApp')",
            "  .controller('BootswatchController', function ($scope, BootSwatchService) {",
            "    /*Get the list of available bootswatch themes*/",
            "    BootSwatchService.get().then(function(themes) {",
            "      $scope.themes = themes;",
            "    });",
            "  });"
        );

        boolean shouldTranslate1 = service.shouldTranslate("docs/tips/009_tips_using_bootswatch_themes.mdx", before1, after1);
        LOGGER.info("LLM decision for typo fix: {}", shouldTranslate1 ? "TRANSLATE" : "SKIP");
        assertThat(shouldTranslate1)
                .as("Typo fix 'availabel' -> 'available' should be skipped")
                .isFalse();

        // Test Case 2: Filename typo fix from commit 9c8b17f - "vulnerabities" -> "vulnerabilities"
        LOGGER.info("Testing filename typo fix: vulnerabities -> vulnerabilities");
        List<String> before2 = List.of(
            "---",
            "title: Dependency Vulnerabilities Check",
            "slug: /dependency-vulnerabities-check/",
            "---"
        );
        List<String> after2 = List.of(
            "---",
            "title: Dependency Vulnerabilities Check",
            "slug: /dependency-vulnerabilities-check/",
            "---"
        );

        boolean shouldTranslate2 = service.shouldTranslate("docs/tests-and-qa/dependency-vulnerabilities-check.mdx", before2, after2);
        LOGGER.info("LLM decision for filename typo fix: {}", shouldTranslate2 ? "TRANSLATE" : "SKIP");
        assertThat(shouldTranslate2)
                .as("Filename typo fix 'vulnerabities' -> 'vulnerabilities' should be skipped")
                .isFalse();

        // Test Case 3: Substantial content change (should require translation)
        LOGGER.info("Testing substantial content change");
        List<String> before3 = List.of(
            "This is the original documentation.",
            "It describes a simple feature."
        );
        List<String> after3 = List.of(
            "This is completely rewritten documentation.",
            "It now describes an advanced feature with many new capabilities.",
            "Additional sections have been added with important details."
        );

        boolean shouldTranslate3 = service.shouldTranslate("docs/example.md", before3, after3);
        LOGGER.info("LLM decision for substantial change: {}", shouldTranslate3 ? "TRANSLATE" : "SKIP");
        assertThat(shouldTranslate3)
                .as("Substantial content changes should require translation")
                .isTrue();

        LOGGER.info("=== Integration test completed successfully ===");
        LOGGER.info("✓ Typo fixes are correctly skipped");
        LOGGER.info("✓ Substantial changes are correctly translated");
    }
}
