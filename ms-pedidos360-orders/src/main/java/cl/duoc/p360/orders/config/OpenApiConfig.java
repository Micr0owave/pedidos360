package cl.duoc.p360.orders.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("ms-pedidos360-orders")
                .version("1.0.0")
                .description("Gestion de pedidos: CRUD, maquina de estados, comandos RabbitMQ y eventos Kafka."))
            .addSecurityItem(new SecurityRequirement().addList(scheme))
            .components(new Components().addSecuritySchemes(scheme,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
