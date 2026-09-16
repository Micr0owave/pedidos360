package cl.duoc.p360.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Perfil "local": desactiva la validacion de JWT para que puedas probar
 * el flujo completo SIN depender de Azure AD todavia.
 * Uso: mvn spring-boot:run -Dspring-boot.run.profiles=local
 * NO usar en la entrega desplegada: ahi va el perfil docker con Azure AD.
 */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
