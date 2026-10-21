package cl.duoc.p360.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.p360.notify.config.MessagingProperties;
import cl.duoc.p360.notify.config.MessagingProperties.Exchanges;
import cl.duoc.p360.notify.config.MessagingProperties.Route;
import cl.duoc.p360.notify.controller.NotifyController;
import cl.duoc.p360.notify.messaging.EventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotifyControllerTest {

    private RabbitTemplate rabbit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rabbit = mock(RabbitTemplate.class);
        Route r = new Route("q.cmd.email", "q.cmd.email.dlq", "email.send", "email.*");
        MessagingProperties props = new MessagingProperties(new Exchanges("cmd.direct", "cmd.topic", "dlx"), 3, r, r, r);
        mvc = MockMvcBuilders.standaloneSetup(new NotifyController(new EventLog(), rabbit, props)).build();
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/notify/test").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void duplicateModePublishesTheSameMessageTwice() throws Exception {
        send("{\"queue\":\"email\",\"mode\":\"duplicate\"}").andExpect(status().isAccepted());
        verify(rabbit, times(2)).convertAndSend(eq("cmd.direct"), eq("email.send"), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void unknownModeIsRejectedWith400AndNothingIsPublished() throws Exception {
        send("{\"queue\":\"email\",\"mode\":\"boom\"}").andExpect(status().isBadRequest());
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }
}
