package cl.duoc.p360.report;

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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Proyeccion de lectura construida SOLO desde Kafka (orders.events).
 * El caso lo exige asi: reporteria por streaming, sin consultar el core.
 */
@SpringBootApplication
public class ReportApplication {
    public static void main(String[] args) { SpringApplication.run(ReportApplication.class, args); }
}

@Entity
@Table(name = "P360_ORDER_PROJECTION")
class OrderProjection {
    @Id @Column(length = 80) private String orderId;
    @Column(length = 30) private String status;
    private Long total;
    private Instant createdAt;
    private Instant deliveredAt;
    @Lob @Column(name = "ITEMS_JSON") private String itemsJson;

    public String getOrderId() { return orderId; }
    public void setOrderId(String v) { this.orderId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getTotal() { return total; }
    public void setTotal(Long v) { this.total = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant v) { this.deliveredAt = v; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String v) { this.itemsJson = v; }
}

interface ProjectionRepository extends JpaRepository<OrderProjection, String> {}

@org.springframework.stereotype.Component
class OrdersEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrdersEventsConsumer.class);
    private final ProjectionRepository repo;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    OrdersEventsConsumer(ProjectionRepository repo) { this.repo = repo; }

    @KafkaListener(topics = "orders.events", groupId = "p360-report")
    @SuppressWarnings("unchecked")
    public void onEvent(Map<String, Object> envelope) throws Exception {
        Object raw = envelope.get("payload");
        if (!(raw instanceof Map)) return;
        Map<String, Object> p = (Map<String, Object>) raw;

        String orderId = String.valueOf(p.get("orderId"));
        OrderProjection proj = repo.findById(orderId).orElseGet(OrderProjection::new);
        proj.setOrderId(orderId);
        proj.setStatus(String.valueOf(p.get("status")));
        proj.setTotal(p.get("total") == null ? 0L : Long.parseLong(p.get("total").toString()));
        proj.setCreatedAt(parse(p.get("createdAt")));
        proj.setDeliveredAt(parse(p.get("deliveredAt")));
        proj.setItemsJson(mapper.writeValueAsString(p.get("items")));
        repo.save(proj);
        log.info("[REPORT] proyeccion actualizada {} -> {}", orderId, proj.getStatus());
    }

    private Instant parse(Object o) {
        if (o == null || "null".equals(o.toString())) return null;
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
}

@RestController
@RequestMapping("/api/report")
@Tag(name = "Reporteria", description = "KPIs: ventas por hora, lead time y estados activos")
class ReportController {

    private final ProjectionRepository repo;
    ReportController(ProjectionRepository repo) { this.repo = repo; }

    /** Ventas agrupadas por hora. */
    @GetMapping("/kpi/sales-by-hour")
    public List<Map<String, Object>> salesByHour() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00").withZone(ZoneId.of("America/Santiago"));
        Map<String, List<OrderProjection>> grouped = repo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(o -> fmt.format(o.getCreatedAt()), TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((hora, list) -> out.add(Map.of(
                "hora", hora,
                "pedidos", list.size(),
                "ventas", list.stream().mapToLong(o -> o.getTotal() == null ? 0 : o.getTotal()).sum())));
        return out;
    }

    /** Lead time = tiempo entre creacion y entrega del pedido. */
    @GetMapping("/kpi/lead-time")
    public Map<String, Object> leadTime() {
        List<Long> minutos = repo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getDeliveredAt() != null)
                .map(o -> Duration.between(o.getCreatedAt(), o.getDeliveredAt()).toMinutes())
                .sorted()
                .toList();

        if (minutos.isEmpty()) {
            return Map.of("muestras", 0, "promedioMin", 0, "medianaMin", 0, "maxMin", 0,
                          "nota", "Aun no hay pedidos ENTREGADOS para calcular lead time");
        }
        double promedio = minutos.stream().mapToLong(Long::longValue).average().orElse(0);
        return Map.of(
                "muestras", minutos.size(),
                "promedioMin", Math.round(promedio),
                "medianaMin", minutos.get(minutos.size() / 2),
                "maxMin", minutos.get(minutos.size() - 1));
    }

    /** Cuantos pedidos hay en cada estado. */
    @GetMapping("/kpi/active-states")
    public Map<String, Long> activeStates() {
        return repo.findAll().stream()
                .collect(Collectors.groupingBy(
                        o -> o.getStatus() == null ? "DESCONOCIDO" : o.getStatus(),
                        TreeMap::new, Collectors.counting()));
    }

    /** Productos mas vendidos, leidos desde la proyeccion. */
    @GetMapping("/kpi/top-products")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> topProducts() {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Integer> acc = new HashMap<>();
        for (OrderProjection o : repo.findAll()) {
            if (o.getItemsJson() == null) continue;
            try {
                List<Map<String, Object>> items = m.readValue(o.getItemsJson(), List.class);
                for (Map<String, Object> it : items) {
                    String pid = String.valueOf(it.get("productId"));
                    int q = Integer.parseInt(String.valueOf(it.get("quantity")));
                    acc.merge(pid, q, Integer::sum);
                }
            } catch (Exception ignored) { }
        }
        return acc.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("productId", e.getKey(), "unidades", e.getValue()))
                .toList();
    }
}

@Configuration
@Profile("!local")
class ReportSecurity {
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
class ReportLocalSecurity {
    @Bean
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
