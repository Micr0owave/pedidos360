package cl.duoc.p360.mqadmin.controller;

import cl.duoc.p360.mqadmin.dto.ExchangeRequest;
import cl.duoc.p360.mqadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final RabbitAdminService adminService;

    public ExchangeController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    /** POST /api/exchanges -> 201 Created */
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody ExchangeRequest request) {
        adminService.createExchange(request);
        return ResponseEntity.created(URI.create("/api/exchanges/" + request.name())).build();
    }

    /** DELETE /api/exchanges/{name} -> 204 No Content */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        adminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }
}
