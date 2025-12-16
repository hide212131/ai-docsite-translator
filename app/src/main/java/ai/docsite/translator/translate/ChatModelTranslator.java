package ai.docsite.translator.translate;

import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Translator backed by a LangChain4j {@link ChatModel} implementation.
 */
public class ChatModelTranslator implements Translator {

    private final ChatModel model;
    private final String providerName;
    private final String modelName;

    public ChatModelTranslator(ChatModel model, String providerName, String modelName) {
        this.model = Objects.requireNonNull(model, "model");
        this.providerName = requireNonBlank(providerName, "providerName");
        this.modelName = requireNonBlank(modelName, "modelName");
    }

    @Override
    public List<String> translate(List<String> sourceLines) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();

        int frontMatterEnd = findFrontMatterEnd(sourceLines);
        if (frontMatterEnd >= 0) {
            for (int i = 0; i <= frontMatterEnd; i++) {
                String line = sourceLines.get(i);
                if (i == 0 || i == frontMatterEnd) {
                    result.add(line);
                } else {
                    result.add(translateFrontMatterLine(line));
                }
            }
        }

        int bodyStart = frontMatterEnd >= 0 ? frontMatterEnd + 1 : 0;
        List<String> body = sourceLines.subList(bodyStart, sourceLines.size());
        List<String> translatedBody = translateBody(body);
        if (translatedBody.isEmpty() && !body.isEmpty()) {
            result.addAll(body);
        } else {
            result.addAll(translatedBody);
        }

        return List.copyOf(result);
    }

    private String buildPrompt(List<String> sourceLines) {
        String joined = String.join("\n", sourceLines);
        return """
Translate the Markdown document below into natural Japanese.
Rules:
- Preserve the existing Markdown structure, including headings, lists, code fences, directives, links, and inline formatting.
- If YAML front matter is present (between lines that contain only `---`), keep its structure exactly as-is and translate only the scalar values; never rename or reorder keys such as `title`, `slug`, or `date`.
- Lines that are exactly `---` must remain `---`; do not add extra separators.
- For lines formatted as `<key>: <value>` inside YAML front matter, keep `<key>` exactly as it appears (case-sensitive) and translate only `<value>` after the colon.
- Keep the exact number of lines as the input. If you need additional spacing, insert blank lines without removing existing ones.
- Do not add, remove, or rename code fence markers. Only translate the text inside them when appropriate, and ensure the number of lines containing only "```" matches the input exactly.
- **IMPORTANT: For minor changes like typo fixes, spelling corrections, or punctuation adjustments, keep them as-is in English. Only translate substantial content changes that affect meaning.**
- Output only the translated markdown as plain text. Do not wrap the result in code fences, do not add commentary, and do not ask for additional input—the document is already provided.

<markdown>
""" + joined + "\n</markdown>";
    }

    private List<String> translateBody(List<String> bodyLines) {
        if (bodyLines.isEmpty()) {
            return List.of();
        }
        try {
            String prompt = buildPrompt(bodyLines);
            String response = model.chat(prompt);
            if (response == null) {
                return List.of();
            }
            List<String> rawLines = Arrays.stream(response.split("\\R", -1))
                    .map(String::stripTrailing)
                    .collect(Collectors.toList());
            return trimExtraCodeFences(rawLines, bodyLines);
        } catch (RuntimeException ex) {
            if (isModelMissing(ex)) {
                throw new TranslationException("%s model '%s' is not available.".formatted(providerName, modelName), ex);
            }
            throw new TranslationException("LangChain translation failed", ex);
        }
    }

    private List<String> trimExtraCodeFences(List<String> translatedLines, List<String> sourceLines) {
        if (translatedLines.isEmpty() || sourceLines.isEmpty()) {
            return translatedLines;
        }
        List<String> adjusted = new ArrayList<>(translatedLines);
        long expectedFenceCount = sourceLines.stream()
                .filter(line -> line.equals("```"))
                .count();
        long actualFenceCount = adjusted.stream()
                .filter(line -> line.equals("```"))
                .count();
        if (actualFenceCount > expectedFenceCount) {
            ListIterator<String> iterator = adjusted.listIterator(adjusted.size());
            while (actualFenceCount > expectedFenceCount && iterator.hasPrevious()) {
                if ("```".equals(iterator.previous())) {
                    iterator.remove();
                    actualFenceCount--;
                }
            }
        }
        while (!adjusted.isEmpty() && adjusted.get(0).isBlank() && !sourceLines.get(0).isBlank()) {
            adjusted.remove(0);
        }
        while (adjusted.size() > sourceLines.size()) {
            boolean removed = false;
            if (!adjusted.isEmpty() && adjusted.get(adjusted.size() - 1).isBlank()) {
                adjusted.remove(adjusted.size() - 1);
                removed = true;
            } else if (!adjusted.isEmpty() && adjusted.get(0).isBlank()) {
                adjusted.remove(0);
                removed = true;
            }
            if (!removed) {
                break;
            }
        }
        return adjusted;
    }

    private int findFrontMatterEnd(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            return -1;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) {
                return i;
            }
        }
        return -1;
    }

    private String translateFrontMatterLine(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0) {
            return line;
        }
        String keyPart = line.substring(0, colonIndex);
        String valuePart = line.substring(colonIndex + 1);
        String trimmedValue = valuePart.trim();
        if (trimmedValue.isEmpty()) {
            return line;
        }

        int start = valuePart.indexOf(trimmedValue);
        int end = start + trimmedValue.length();
        String prefix = start >= 0 ? valuePart.substring(0, start) : "";
        String suffix = end >= 0 && end <= valuePart.length() ? valuePart.substring(end) : "";

        String translated = translateScalarValue(trimmedValue);
        if (translated.isBlank()) {
            translated = trimmedValue;
        }
        return keyPart + ":" + prefix + translated + suffix;
    }

    private String translateScalarValue(String value) {
        String prompt = """
Translate the following short phrase into natural Japanese.
Return only the translated phrase without additional punctuation or commentary.

<text>
""" + value + "\n</text>";
        try {
            String response = model.chat(prompt);
            if (response == null) {
                return value;
            }
            String cleaned = response.strip();
            if (cleaned.isEmpty() || cleaned.contains("<text>")) {
                return value;
            }
            return cleaned;
        } catch (RuntimeException ex) {
            if (isModelMissing(ex)) {
                throw new TranslationException("%s model '%s' is not available.".formatted(providerName, modelName), ex);
            }
            return value;
        }
    }

    private boolean isModelMissing(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ModelNotFoundException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
