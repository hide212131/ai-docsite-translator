package ai.docsite.translator.translate;

import java.util.Objects;

/**
 * Provides translator instances based on the desired execution mode.
 */
public class TranslatorFactory {

    private final Translator productionTranslator;
    private final Translator dryRunTranslator;
    private final Translator mockTranslator;

    public TranslatorFactory(Translator productionTranslator,
                             Translator dryRunTranslator,
                             Translator mockTranslator) {
        this.productionTranslator = Objects.requireNonNull(productionTranslator, "productionTranslator");
        this.dryRunTranslator = Objects.requireNonNull(dryRunTranslator, "dryRunTranslator");
        this.mockTranslator = Objects.requireNonNull(mockTranslator, "mockTranslator");
    }

    public Translator select(TranslationMode mode) {
        return switch (mode) {
            case PRODUCTION -> productionTranslator;
            case DRY_RUN -> dryRunTranslator;
            case MOCK -> mockTranslator;
        };
    }
}
