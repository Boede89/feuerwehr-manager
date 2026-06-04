package de.feuerwehr.manager.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ContainerLogConfig {

    private final ContainerLogBuffer containerLogBuffer;

    @PostConstruct
    void attachAppender() {
        ContainerLogAppender.register(containerLogBuffer);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        ContainerLogAppender appender = new ContainerLogAppender();
        appender.setContext(context);
        appender.setName("CONTAINER_LOG_BUFFER");
        appender.start();
        root.addAppender(appender);
    }
}
