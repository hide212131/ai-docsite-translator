package ai.docsite.translator.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal JSON layout for logback without external dependencies.
 */
public class SimpleJsonLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder builder = new StringBuilder(256);
        builder.append('{');
        appendField(builder, "timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()).atOffset(ZoneOffset.UTC)));
        builder.append(',');
        appendField(builder, "level", event.getLevel().toString());
        builder.append(',');
        appendField(builder, "logger", event.getLoggerName());
        builder.append(',');
        appendField(builder, "thread", event.getThreadName());
        builder.append(',');
        appendField(builder, "message", event.getFormattedMessage());

        Map<String, String> mdc = safeMdc(event);
        if (!mdc.isEmpty()) {
            builder.append(',');
            builder.append("\"mdc\":{");
            String payload = mdc.entrySet().stream()
                    .map(entry -> quote(entry.getKey()) + ':' + quote(entry.getValue()))
                    .collect(Collectors.joining(","));
            builder.append(payload);
            builder.append('}');
        }

        builder.append('}');
        builder.append(System.lineSeparator());
        return builder.toString();
    }

    private void appendField(StringBuilder builder, String name, String value) {
        builder.append(quote(name)).append(':').append(quote(value));
    }

    private Map<String, String> safeMdc(ILoggingEvent event) {
        try {
            Map<String, String> map = event.getMDCPropertyMap();
            return map == null ? Map.of() : map;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }
}
