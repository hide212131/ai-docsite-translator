package ai.docsite.translator.cli;

import ai.docsite.translator.translate.TranslationMode;
import picocli.CommandLine;

public class TranslationModeConverter implements CommandLine.ITypeConverter<TranslationMode> {

    @Override
    public TranslationMode convert(String value) {
        return TranslationMode.from(value);
    }
}
