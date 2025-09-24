package ai.docsite.translator.translate;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Translator that delegates to Gemini via LangChain4j.
 */
public class GeminiTranslator implements Translator {

    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final ChatModel model;

    public GeminiTranslator(String apiKey) {
        this(createModel(Objects.requireNonNull(apiKey, "apiKey")));
    }

    GeminiTranslator(ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public List<String> translate(List<String> sourceLines) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return List.of();
        }
        try {
            String prompt = buildPrompt(sourceLines);
            String response = model.chat(prompt);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response.split("\\R", -1))
                    .map(String::stripTrailing)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw new TranslationException("Gemini translation failed", ex);
        }
    }

    private static ChatModel createModel(String apiKey) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .timeout(REQUEST_TIMEOUT)
                .temperature(0.1)
                .build();
    }

    private String buildPrompt(List<String> sourceLines) {
        String joined = String.join("\n", sourceLines);
        return """
                Translate the following Markdown content into natural Japanese.
                Requirements:
                - Preserve the original markdown structure, code fences, front matter, anchor links, directives, and inline formatting.
                - Keep the same number of lines as the input. Insert blank lines as needed to align line numbers.
                - Do not add commentary or explanations. Output only the translated markdown.

                ```markdown
                """ + joined + "\n```";
    }
}
