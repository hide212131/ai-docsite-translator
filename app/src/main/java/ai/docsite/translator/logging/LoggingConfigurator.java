package ai.docsite.translator.logging;

import ai.docsite.translator.config.LogFormat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.slf4j.LoggerFactory;

/**
 * Applies runtime logback configuration updates such as switching encoder formats.
 */
public final class LoggingConfigurator {

    private static final String TEXT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n";

    private LoggingConfigurator() {
    }

    public static void configure(LogFormat format) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            Appender<ILoggingEvent> appender = iterator.next();
            if (appender instanceof OutputStreamAppender<ILoggingEvent> outputStreamAppender) {
                switch (format) {
                    case JSON -> applyJsonEncoder(context, outputStreamAppender);
                    case TEXT -> applyTextEncoder(context, outputStreamAppender);
                }
            }
        }
    }

    private static void applyJsonEncoder(LoggerContext context,
                                          OutputStreamAppender<ILoggingEvent> appender) {
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(new SimpleJsonLayout());
        encoder.start();
        restartAppender(appender, encoder);
    }

    private static void applyTextEncoder(LoggerContext context,
                                          OutputStreamAppender<ILoggingEvent> appender) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(TEXT_PATTERN);
        encoder.start();
        restartAppender(appender, encoder);
    }

    private static void restartAppender(OutputStreamAppender<ILoggingEvent> appender,
                                        ch.qos.logback.core.encoder.Encoder<ILoggingEvent> encoder) {
        boolean running = appender.isStarted();
        if (running) {
            appender.stop();
        }
        appender.setEncoder(encoder);
        if (running) {
            appender.start();
        }
    }
}
