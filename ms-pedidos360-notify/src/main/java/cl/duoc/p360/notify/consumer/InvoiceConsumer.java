package cl.duoc.p360.notify.consumer;

import cl.duoc.p360.notify.config.MessagingProperties;
import cl.duoc.p360.notify.messaging.Delivery;
import cl.duoc.p360.notify.messaging.MessageAcknowledger;
import cl.duoc.p360.notify.service.InvoiceService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Dominio: boletas y facturas. */
@Component
public class InvoiceConsumer {

    private final InvoiceService service;
    private final MessageAcknowledger acknowledger;
    private final MessagingProperties props;

    public InvoiceConsumer(InvoiceService service, MessageAcknowledger acknowledger, MessagingProperties props) {
        this.service = service;
        this.acknowledger = acknowledger;
        this.props = props;
    }

    @RabbitListener(queues = "${p360.messaging.invoice.queue}")
    public void onInvoice(Map<String, Object> envelope,
                    Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long tag,
                    @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                    @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                    @Header(name = MessageAcknowledger.RETRY_HEADER, required = false) Integer retries)
            throws IOException {
        Delivery delivery = new Delivery(channel, tag, messageId, correlationId, retries);
        acknowledger.process(props.invoice(), envelope, delivery, () -> service.generate(envelope));
    }
}
