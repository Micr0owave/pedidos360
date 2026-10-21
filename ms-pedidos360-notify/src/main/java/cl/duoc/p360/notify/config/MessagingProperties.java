package cl.duoc.p360.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y routing keys de RabbitMQ, leidos desde application.yml (p360.messaging).
 * No hay nombres escritos en el codigo.
 */
@ConfigurationProperties(prefix = "p360.messaging")
public record MessagingProperties(Exchanges exchanges, int maxRetries, Route email, Route kitchen, Route invoice) {

    public record Exchanges(String direct, String topic, String deadLetter) { }

    public record Route(String queue, String deadLetterQueue, String routingKey, String topicPattern) { }
}
