package cl.duoc.p360.mqadmin.controller;

import cl.duoc.p360.mqadmin.dto.BindingRequest;
import cl.duoc.p360.mqadmin.dto.DestinationKind;
import cl.duoc.p360.mqadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/bindings")
public class BindingController {

    private final RabbitAdminService adminService;

    public BindingController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    /** POST /api/bindings -> 201 Created */
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody BindingRequest request) {
        adminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** DELETE /api/bindings?exchange=&destination=&destinationType=&routingKey= -> 204 No Content */
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestParam @NotBlank(message = "exchange es obligatorio") String exchange,
            @RequestParam @NotBlank(message = "destination es obligatorio") String destination,
            @RequestParam @NotNull(message = "destinationType es obligatorio") DestinationKind destinationType,
            @RequestParam(required = false, defaultValue = "") String routingKey) {
        adminService.deleteBinding(new BindingRequest(exchange, destination, destinationType, routingKey));
        return ResponseEntity.noContent().build();
    }
}
