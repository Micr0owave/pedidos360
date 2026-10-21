package cl.duoc.p360.mqadmin;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.p360.mqadmin.config.AdminProperties;
import cl.duoc.p360.mqadmin.dto.QueueRequest;
import cl.duoc.p360.mqadmin.exception.InvalidRequestException;
import cl.duoc.p360.mqadmin.exception.ResourceNotFoundException;
import cl.duoc.p360.mqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

class RabbitAdminServiceTest {

    private RabbitAdmin rabbitAdmin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        rabbitAdmin = mock(RabbitAdmin.class);
        service = new RabbitAdminService(rabbitAdmin, new AdminProperties("amq.", 255));
    }

    @Test
    void createQueueDeclaresItInTheBroker() {
        when(rabbitAdmin.getQueueInfo("mi.cola")).thenReturn(new org.springframework.amqp.core.QueueInformation("mi.cola", 0, 0));
        service.createQueue(new QueueRequest("mi.cola", true, false, null, null, null));
        verify(rabbitAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void reservedNamesAreRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.createQueue(new QueueRequest("amq.secreta", null, null, null, null, null)));
        verify(rabbitAdmin, never()).declareQueue(any());
    }

    @Test
    void deletingMissingQueueFailsWithNotFound() {
        when(rabbitAdmin.getQueueInfo("nada")).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteQueue("nada"));
        verify(rabbitAdmin, never()).deleteQueue("nada");
    }
}
