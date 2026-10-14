package cl.duoc.p360.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.p360.notify.config.MessagingProperties;
import cl.duoc.p360.notify.config.MessagingProperties.Exchanges;
import cl.duoc.p360.notify.config.MessagingProperties.Route;
import cl.duoc.p360.notify.exception.InvalidMessageException;
import cl.duoc.p360.notify.exception.TransientProcessingException;
import cl.duoc.p360.notify.messaging.Delivery;
import cl.duoc.p360.notify.messaging.IdempotencyGuard;
import cl.duoc.p360.notify.messaging.MessageAcknowledger;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class MessageAcknowledgerTest {

    private static final long TAG = 7L;

    private final Route route = new Route("q", "q.dlq", "rk", "rk.*");
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private MessageAcknowledger acknowledger;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        MessagingProperties props = new MessagingProperties(new Exchanges("direct", "topic", "dlx"), 3, route, route, route);
        acknowledger = new MessageAcknowledger(rabbitTemplate, props, new IdempotencyGuard());
    }

    private Delivery delivery(String messageId, Integer retries) {
        return new Delivery(channel, TAG, messageId, "corr-1", retries);
    }

    @Test
    void successIsAcked() throws Exception {
        acknowledger.process(route, "m", delivery("id1", null), () -> { });
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void duplicateIsAckedWithoutRunningTheWorkAgain() throws Exception {
        int[] runs = {0};
        acknowledger.process(route, "m", delivery("dup", null), () -> runs[0]++);
        acknowledger.process(route, "m", delivery("dup", null), () -> runs[0]++);
        org.junit.jupiter.api.Assertions.assertEquals(1, runs[0]);
    }

    @Test
    void invalidMessageGoesStraightToDlq() throws Exception {
        acknowledger.process(route, "m", delivery("id2", null), () -> { throw new InvalidMessageException("mal"); });
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(eq(TAG), eq(false));
    }

    @Test
    void transientErrorIsRepublishedAndAcked() throws Exception {
        acknowledger.process(route, "m", delivery("id3", 1), () -> { throw new TransientProcessingException("caido"); });
        verify(rabbitTemplate).convertAndSend(eq("direct"), eq("rk"), eq((Object) "m"), any(MessagePostProcessor.class));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void transientErrorGoesToDlqWhenRetriesAreExhausted() throws Exception {
        acknowledger.process(route, "m", delivery("id4", 3), () -> { throw new TransientProcessingException("caido"); });
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(eq(TAG), eq(false));
    }
}
