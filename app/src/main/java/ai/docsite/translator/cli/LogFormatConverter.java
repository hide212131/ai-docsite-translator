package ai.docsite.translator.cli;

import ai.docsite.translator.config.LogFormat;
import picocli.CommandLine;

/**
 * Parses log format CLI options.
 */
public class LogFormatConverter implements CommandLine.ITypeConverter<LogFormat> {
    @Override
    public LogFormat convert(String value) {
        return LogFormat.from(value);
    }
}
