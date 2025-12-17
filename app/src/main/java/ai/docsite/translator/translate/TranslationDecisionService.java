package ai.docsite.translator.translate;

import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses LLM to determine if file changes require translation or are minor (typos, formatting).
 */
public class TranslationDecisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationDecisionService.class);

    private final ChatModel chatModel;

    public TranslationDecisionService(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    /**
     * Asks LLM to decide if the diff requires translation.
     *
     * @param filePath the file path being analyzed
     * @param baseLines the original content
     * @param newLines the new content
     * @return true if translation is needed, false if changes are minor (typos, formatting)
     */
    public boolean shouldTranslate(String filePath, List<String> baseLines, List<String> newLines) {
        if (newLines.isEmpty()) {
            return false;
        }

        // If no base content, it's a new file that needs full translation
        if (baseLines.isEmpty()) {
            return true;
        }

        String prompt = buildDecisionPrompt(filePath, baseLines, newLines);
        
        try {
            String response = chatModel.chat(prompt);
            if (response == null || response.isBlank()) {
                // Default to translating if LLM doesn't respond
                LOGGER.warn("LLM returned empty response for translation decision on {}, defaulting to translate", filePath);
                return true;
            }

            // Parse response - expect "YES" or "NO"
            String normalized = response.trim().toUpperCase();
            boolean shouldTranslate = normalized.contains("YES") || normalized.startsWith("Y");
            
            if (shouldTranslate) {
                LOGGER.info("LLM decided to translate {} (substantial changes)", filePath);
            } else {
                LOGGER.info("LLM decided to skip translation for {} (minor changes only)", filePath);
            }
            
            return shouldTranslate;
        } catch (RuntimeException ex) {
            LOGGER.warn("Error getting translation decision from LLM for {}: {}, defaulting to translate", 
                    filePath, ex.getMessage());
            return true;
        }
    }

    private String buildDecisionPrompt(String filePath, List<String> baseLines, List<String> newLines) {
        String baseDiff = String.join("\n", baseLines);
        String newDiff = String.join("\n", newLines);

        return """
You are analyzing changes to an English documentation file to determine if they require re-translation to Japanese.

File: %s

=== ORIGINAL CONTENT ===
%s

=== NEW CONTENT ===
%s

=== TASK ===
Determine if these changes require re-translation or are minor edits that don't affect meaning:
- **Translation needed (respond YES)**: Content additions, rewrites, structural changes, new sections, meaning changes
- **No translation needed (respond NO)**: Typo fixes, spelling corrections, punctuation changes, whitespace adjustments, minor formatting

Respond with ONLY one word: "YES" if translation is needed, or "NO" if changes are minor and don't require re-translation.
""".formatted(filePath, baseDiff, newDiff);
    }
}
