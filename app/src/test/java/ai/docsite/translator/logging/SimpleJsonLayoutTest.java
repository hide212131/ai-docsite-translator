package ai.docsite.translator.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

class SimpleJsonLayoutTest {

    @Test
    void formatsEventAsJson() {
        LoggerContext context = new LoggerContext();
        context.start();
        SimpleJsonLayout layout = new SimpleJsonLayout();
        layout.setContext(context);
        layout.start();
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setLoggerName("test.logger");
        event.setMessage("hello world");
        event.setThreadName("main");
        event.setTimeStamp(0L);
        event.setLoggerContext(context);

        String json;
        try {
            json = layout.doLayout(event);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }

        assertThat(json).contains("\"message\":\"hello world\"");
        assertThat(json).contains("\"logger\":\"test.logger\"");
        assertThat(json).contains("\"level\":\"INFO\"");
        assertThat(json).endsWith(System.lineSeparator());
    }
}
