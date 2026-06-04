package de.feuerwehr.manager.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Leitet Logback-Ereignisse in den ContainerLogBuffer. */
public class ContainerLogAppender extends AppenderBase<ILoggingEvent> {

    private static ContainerLogBuffer buffer;

    public static void register(ContainerLogBuffer containerLogBuffer) {
        buffer = containerLogBuffer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (buffer == null || !isStarted()) {
            return;
        }
        buffer.append(event.getLevel().toString(), event.getLoggerName(), event.getFormattedMessage());
    }
}
