package de.feuerwehr.manager.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/** Ringpuffer für die letzten Anwendungs-Logzeilen (Admin → Container-Log). */
@Component
public class ContainerLogBuffer {

    private static final int MAX_LINES = 1000;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Deque<String> lines = new ConcurrentLinkedDeque<>();

    public void append(String level, String logger, String message) {
        String line = TS.format(Instant.now()) + " [" + level + "] " + shortenLogger(logger) + " – " + message;
        synchronized (lines) {
            lines.addLast(line);
            while (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }
    }

    public List<String> snapshot() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    private static String shortenLogger(String logger) {
        if (logger == null) {
            return "app";
        }
        int dot = logger.lastIndexOf('.');
        return dot >= 0 ? logger.substring(dot + 1) : logger;
    }
}
