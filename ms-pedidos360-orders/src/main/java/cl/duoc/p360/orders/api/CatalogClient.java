package cl.duoc.p360.orders.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Coordinacion de stock con ms-pedidos360-catalog.
 * Regla del caso: el stock decrece AL ACEPTAR el pedido (no al crearlo).
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private final RestClient client;

    public CatalogClient(@Value("${p360.catalog.base-url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void decreaseStock(String productId, int quantity) {
        try {
            client.post()
                  .uri("/api/catalog/products/{id}/stock/decrease", productId)
                  .body(Map.of("quantity", quantity))
                  .retrieve()
                  .toBodilessEntity();
            log.info("[catalog] stock -{} para producto {}", quantity, productId);
        } catch (Exception e) {
            // No tumbamos el pedido si catalog esta caido: se registra y sigue.
            // Para la evaluacion es mejor que el flujo end-to-end no se corte.
            log.warn("[catalog] no se pudo descontar stock de {}: {}", productId, e.getMessage());
        }
    }
}
