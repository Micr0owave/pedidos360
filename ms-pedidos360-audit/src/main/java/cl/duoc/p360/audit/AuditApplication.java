package cl.duoc.p360.audit;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class AuditApplication {
    public static void main(String[] args) { SpringApplication.run(AuditApplication.class, args); }
}

@Entity
@Table(name = "P360_AUDIT_EVENTS")
class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 100) private String eventId;
    @Column(length = 80)  private String type;
    @Column(length = 80)  private String entityId;     // orderId
    @Column(length = 150) private String actor;        // quien
    private Instant occurredAt;                        // cuando
    @Column(length = 100) private String correlationId;
    @Lob @Column(name = "PAYLOAD_JSON") private String payloadJson;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String v) { this.eventId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String v) { this.entityId = v; }
    public String getActor() { return actor; }
    public void setActor(String v) { this.actor = v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { this.occurredAt = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { this.correlationId = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
}

interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByEntityIdOrderByOccurredAtAsc(String entityId);
    List<AuditEvent> findByActorOrderByOccurredAtDesc(String actor);
    List<AuditEvent> findByTypeOrderByOccurredAtDesc(String type);
    List<AuditEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant from, Instant to);
}

/** Consume audit.timeline y persiste "quien / que / cuando". */
@org.springframework.stereotype.Component
class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);
    private final AuditRepository repo;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    AuditConsumer(AuditRepository repo) { this.repo = repo; }

    @KafkaListener(topics = "audit.timeline", groupId = "p360-audit")
    public void onEvent(Map<String, Object> envelope) throws Exception {
        AuditEvent e = new AuditEvent();
        e.setEventId(str(envelope.get("eventId")));
        e.setType(str(envelope.get("type")));
        e.setCorrelationId(str(envelope.get("correlationId")));
        e.setActor(str(envelope.get("actor")));
        e.setOccurredAt(parse(str(envelope.get("timestamp"))));
        Object payload = envelope.get("payload");
        if (payload instanceof Map<?, ?> p) {
            e.setEntityId(str(p.get("orderId")));
        }
        e.setPayloadJson(mapper.writeValueAsString(payload));
        repo.save(e);
        log.info("[AUDIT] {} sobre {}", e.getType(), e.getEntityId());
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
    private Instant parse(String s) {
        try { return s == null ? Instant.now() : Instant.parse(s); }
        catch (Exception ex) { return Instant.now(); }
    }
}

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Auditoria", description = "Timeline de eventos de negocio (solo lectura)")
class AuditController {

    private final AuditRepository repo;
    AuditController(AuditRepository repo) { this.repo = repo; }

    @GetMapping("/events")
    public List<AuditEvent> events(@RequestParam(required = false) String entityId,
                                   @RequestParam(required = false) String actor,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to) {
        if (entityId != null) return repo.findByEntityIdOrderByOccurredAtAsc(entityId);
        if (actor != null)    return repo.findByActorOrderByOccurredAtDesc(actor);
        if (type != null)     return repo.findByTypeOrderByOccurredAtDesc(type);
        if (from != null && to != null)
            return repo.findByOccurredAtBetweenOrderByOccurredAtDesc(Instant.parse(from), Instant.parse(to));
        return repo.findAll();
    }

    @GetMapping("/timeline/{orderId}")
    public List<AuditEvent> timeline(@PathVariable String orderId) {
        return repo.findByEntityIdOrderByOccurredAtAsc(orderId);
    }
}

@Configuration
@Profile("!local")
class AuditSecurity {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }
}

@Configuration
@Profile("local")
class AuditLocalSecurity {
    @Bean
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
