package cl.duoc.p360.notify.messaging;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/** Ultimos resultados del consumidor (en memoria), para mostrarlos en el frontend sin abrir los logs. */
@Component
public class EventLog {

    private static final int MAX = 50;

    public record Entry(Instant time, String queue, String result, String reason, String correlationId) { }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public synchronized void record(String queue, String result, String reason, String correlationId) {
        if (entries.size() == MAX) entries.removeLast();
        entries.addFirst(new Entry(Instant.now(), queue, result, reason, correlationId));
    }

    /** Del mas reciente al mas antiguo. */
    public synchronized List<Entry> recent() {
        List<Entry> copy = new ArrayList<>(entries);
        return Collections.unmodifiableList(copy);
    }
}
