package cl.duoc.p360.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Valida el JWT emitido por Azure AD (issuer-uri en application.yml)
 * y mapea los "roles" del token a authorities ROLE_*.
 * En Azure AD los App Roles llegan en el claim "roles".
 */
@Configuration
@Profile("!local")
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            );
        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtConverter() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
            }
            // fallback: scopes
            String scp = jwt.getClaimAsString("scp");
            if (scp != null) {
                for (String s : scp.split(" ")) authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
            }
            return new JwtAuthenticationToken(jwt, authorities);
        };
    }
}
