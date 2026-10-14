package cl.duoc.p360.notify.consumer;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor de las DLQ: solo registra el mensaje muerto (evidencia de DLQ) y lo confirma.
 * Con acknowledge-mode manual hay que hacer ACK explicito tambien aqui.
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    @RabbitListener(queues = {
            "${p360.messaging.email.dead-letter-queue}",
            "${p360.messaging.kitchen.dead-letter-queue}",
            "${p360.messaging.invoice.dead-letter-queue}"})
    public void onDeadLetter(Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        log.error("[DLQ] mensaje muerto en {} -> {}",
                message.getMessageProperties().getConsumerQueue(),
                new String(message.getBody()));
        channel.basicAck(tag, false);
    }
}
