package cl.duoc.p360.mqadmin;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.p360.mqadmin.controller.QueueController;
import cl.duoc.p360.mqadmin.exception.GlobalExceptionHandler;
import cl.duoc.p360.mqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueueControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        QueueController controller = new QueueController(mock(RabbitAdminService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void emptyNameIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCharactersAreRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cola con espacios\"}"))
                .andExpect(status().isBadRequest());
    }
}
