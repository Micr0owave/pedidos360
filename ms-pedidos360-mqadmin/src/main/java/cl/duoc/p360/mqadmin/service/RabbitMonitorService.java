package cl.duoc.p360.mqadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Solo lectura: consulta la API de gestion de RabbitMQ (puerto 15672, la misma de la consola web)
 * para listar colas con sus contadores y para ver mensajes sin consumirlos.
 */
@Service
public class RabbitMonitorService {

    public record QueueSummary(String name, int messages, int ready, int unacked, int consumers, boolean deadLetter) { }

    public record PeekedMessage(String routingKey, boolean redelivered, String payload) { }

    private final String baseUrl;
    private final RestClient http;

    public RabbitMonitorService(@Value("${p360.mqadmin.management-url}") String baseUrl,
                                @Value("${spring.rabbitmq.username}") String user,
                                @Value("${spring.rabbitmq.password}") String pass) {
        this.baseUrl = baseUrl;
        this.http = RestClient.builder().defaultHeaders(h -> h.setBasicAuth(user, pass)).build();
    }

    public List<QueueSummary> queues() {
        JsonNode all = http.get().uri(uri("api", "queues", "/")).retrieve().body(JsonNode.class);
        List<QueueSummary> out = new ArrayList<>();
        for (JsonNode q : all) {
            out.add(new QueueSummary(q.path("name").asText(), q.path("messages").asInt(),
                    q.path("messages_ready").asInt(), q.path("messages_unacknowledged").asInt(),
                    q.path("consumers").asInt(), q.path("arguments").has("x-dead-letter-exchange")));
        }
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    /** Mira los primeros mensajes de una cola y los deja donde estaban (ack_requeue_true). */
    public List<PeekedMessage> peek(String queue, int count) {
        JsonNode msgs = http.post().uri(uri("api", "queues", "/", queue, "get"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("count", count, "ackmode", "ack_requeue_true", "encoding", "auto", "truncate", 5000))
                .retrieve().body(JsonNode.class);
        List<PeekedMessage> out = new ArrayList<>();
        for (JsonNode m : msgs) {
            out.add(new PeekedMessage(m.path("routing_key").asText(), m.path("redelivered").asBoolean(),
                    m.path("payload").asText()));
        }
        return out;
    }

    /** El vhost "/" se escribe %2F en la URL; pathSegment lo codifica solo. */
    private URI uri(String... segments) {
        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment(segments).build().encode().toUri();
    }
}
