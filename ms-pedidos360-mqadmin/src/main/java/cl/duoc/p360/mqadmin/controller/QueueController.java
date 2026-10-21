package cl.duoc.p360.mqadmin.controller;

import cl.duoc.p360.mqadmin.dto.QueueInfoResponse;
import cl.duoc.p360.mqadmin.dto.QueueRequest;
import cl.duoc.p360.mqadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final RabbitAdminService adminService;

    public QueueController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    /** POST /api/queues -> 201 Created */
    @PostMapping
    public ResponseEntity<QueueInfoResponse> create(@Valid @RequestBody QueueRequest request) {
        QueueInfoResponse created = adminService.createQueue(request);
        return ResponseEntity.created(URI.create("/api/queues/" + created.name())).body(created);
    }

    /** GET /api/queues/{name} -> 200 con mensajes y consumidores, o 404 */
    @GetMapping("/{name}")
    public QueueInfoResponse get(@PathVariable String name) {
        return adminService.getQueue(name);
    }

    /** DELETE /api/queues/{name} -> 204 No Content, o 404 */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        adminService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }
}
