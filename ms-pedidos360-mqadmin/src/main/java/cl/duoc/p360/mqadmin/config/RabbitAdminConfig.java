package cl.duoc.p360.mqadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuración de infraestructura: expone RabbitAdmin, la API de Spring AMQP para administrar el broker. */
@Configuration
public class RabbitAdminConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // No declara nada solo: este servicio crea y borra únicamente lo que pide la API REST.
        admin.setAutoStartup(false);
        return admin;
    }
}
