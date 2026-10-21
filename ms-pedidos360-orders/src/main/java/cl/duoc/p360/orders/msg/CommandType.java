package cl.duoc.p360.orders.msg;

/** Tipos de comando que el sistema envia por RabbitMQ. El nombre real de cada ruta vive en application.yml. */
public enum CommandType { EMAIL, KITCHEN, INVOICE }
