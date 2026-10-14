package cl.duoc.p360.mqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Datos para crear un binding entre un exchange y una cola (u otro exchange). */
public record BindingRequest(
        @NotBlank(message = "El exchange de origen es obligatorio") String exchange,
        @NotBlank(message = "El destino es obligatorio") String destination,
        @NotNull(message = "destinationType es obligatorio (QUEUE o EXCHANGE)") DestinationKind destinationType,
        String routingKey) {
}
