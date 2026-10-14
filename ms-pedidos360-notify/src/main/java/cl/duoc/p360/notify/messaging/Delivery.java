package cl.duoc.p360.notify.messaging;

import com.rabbitmq.client.Channel;

/** Datos de entrega de un mensaje que el consumidor necesita para confirmarlo o rechazarlo. */
public record Delivery(Channel channel, long tag, String messageId, String correlationId, Integer retryCount) { }
