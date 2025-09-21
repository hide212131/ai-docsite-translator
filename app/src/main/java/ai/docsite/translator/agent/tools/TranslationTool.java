package ai.docsite.translator.agent.tools;

import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.translate.TranslationService.TranslationSummary;
import dev.langchain4j.agent.tool.Tool;
import java.util.Objects;

/**
 * Tool invoking the translation service.
 */
public class TranslationTool {

    private final TranslationService translationService;
    private final DiffMetadata metadata;

    public TranslationTool(TranslationService translationService, DiffMetadata metadata) {
        this.translationService = Objects.requireNonNull(translationService, "translationService");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    @Tool(name = "translateDiff", value = "Translate all pending documentation changes")
    public TranslationSummary translate() {
        return translationService.translateAll(metadata);
    }
}
