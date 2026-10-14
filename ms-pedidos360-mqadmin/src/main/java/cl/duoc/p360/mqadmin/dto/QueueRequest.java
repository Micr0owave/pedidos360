package cl.duoc.p360.mqadmin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear una cola. Solo el nombre es obligatorio. */
public record QueueRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "El nombre solo admite letras, números, '.', '_' y '-'")
        String name,

        Boolean durable,
        Boolean autoDelete,

        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "deadLetterExchange tiene caracteres inválidos")
        String deadLetterExchange,

        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "deadLetterRoutingKey tiene caracteres inválidos")
        String deadLetterRoutingKey,

        @Min(value = 1, message = "messageTtlMs debe ser mayor que 0")
        Integer messageTtlMs) {
}
