package ai.docsite.translator.cli;

import ai.docsite.translator.config.Mode;
import picocli.CommandLine;

public class ModeConverter implements CommandLine.ITypeConverter<Mode> {

    @Override
    public Mode convert(String value) {
        return Mode.from(value);
    }
}
