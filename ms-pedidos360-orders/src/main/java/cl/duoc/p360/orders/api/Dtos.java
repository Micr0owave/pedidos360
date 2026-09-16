package cl.duoc.p360.orders.api;

import cl.duoc.p360.orders.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public class Dtos {

    public record ItemRequest(
            @NotBlank String productId,
            @NotNull @Min(1) Integer quantity,
            @NotNull @Min(0) Long unitPrice
    ) {}

    public record CreateOrderRequest(
            @Email String customerEmail,
            @NotEmpty @Valid List<ItemRequest> items
    ) {}

    public record ChangeStatusRequest(
            @NotNull OrderStatus target,
            String reason
    ) {}

    public record ErrorResponse(String error, String message, Object details) {}
}
