package cl.duoc.p360.mqadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros del administrador, leídos desde application.yml (prefijo p360.mqadmin). */
@ConfigurationProperties(prefix = "p360.mqadmin")
public record AdminProperties(String reservedPrefix, int maxNameLength) {
}
