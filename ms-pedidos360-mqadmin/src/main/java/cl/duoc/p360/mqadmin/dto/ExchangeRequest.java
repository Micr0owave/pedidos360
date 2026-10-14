package cl.duoc.p360.mqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear un exchange. */
public record ExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "El nombre solo admite letras, números, '.', '_' y '-'")
        String name,

        @NotNull(message = "El tipo es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        ExchangeKind type,

        Boolean durable,
        Boolean autoDelete) {
}
