package cl.duoc.p360.mqadmin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Perfil "local": sin validacion de JWT para probar sin Azure AD. NO usar en la entrega desplegada. */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
