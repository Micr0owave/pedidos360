package cl.duoc.p360.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y routing keys de RabbitMQ.
 * Se leen desde application.yml (prefijo p360.messaging): no hay nombres escritos en el codigo.
 */
@ConfigurationProperties(prefix = "p360.messaging")
public record MessagingProperties(Exchanges exchanges, Route email, Route kitchen, Route invoice) {

    public record Exchanges(String direct, String topic, String deadLetter) { }

    /** Un caso de uso: cola principal + su DLQ + routing key directa + patron para el exchange topic. */
    public record Route(String queue, String deadLetterQueue, String routingKey, String topicPattern) { }
}
