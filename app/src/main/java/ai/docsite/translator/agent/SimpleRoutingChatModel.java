package ai.docsite.translator.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

/**
 * Temporary heuristic chat model that returns deterministic plans until a real planner model is wired.
 */
public class SimpleRoutingChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String prompt = extractLastUserMessage(request.messages());
        String plan = planForPrompt(prompt);
        AiMessage aiMessage = new AiMessage(plan);
        return ChatResponse.builder().aiMessage(aiMessage).build();
    }

    private String extractLastUserMessage(List<ChatMessage> messages) {
        String last = "";
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                last = userMessage.singleText();
            }
        }
        return last;
    }

    private String planForPrompt(String prompt) {
        if (prompt == null) {
            return "NO_ACTION";
        }
        String upperPrompt = prompt.toUpperCase();
        boolean isDev = upperPrompt.contains("MODE=DEV");
        boolean hasChanges = !upperPrompt.contains("Changes={}");
        if (!hasChanges) {
            return "NO_ACTION";
        }
        if (isDev) {
            return "REVIEW_ONLY_CREATE_PR";
        }
        return "TRANSLATE_AND_CREATE_PR";
    }
}
