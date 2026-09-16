package cl.duoc.p360.bff;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;

/**
 * Backend For Frontend.
 * Recibe el request del Angular (ya validado por API Gateway), vuelve a validar
 * el JWT con Spring Security y reenvia al microservicio correspondiente
 * propagando el header Authorization.
 */
@SpringBootApplication
public class BffApplication {
    public static void main(String[] args) { SpringApplication.run(BffApplication.class, args); }
}

@Configuration
class BffClients {
    @Bean RestClient ordersClient(@Value("${p360.orders.base-url}") String u)  { return RestClient.create(u); }
    @Bean RestClient catalogClient(@Value("${p360.catalog.base-url}") String u){ return RestClient.create(u); }
    @Bean RestClient auditClient(@Value("${p360.audit.base-url}") String u)    { return RestClient.create(u); }
    @Bean RestClient reportClient(@Value("${p360.report.base-url}") String u)  { return RestClient.create(u); }
}

@RestController
@Tag(name = "BFF", description = "Punto unico de entrada del frontend")
class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final Map<String, RestClient> routes = new LinkedHashMap<>();

    GatewayController(RestClient ordersClient, RestClient catalogClient,
                      RestClient auditClient, RestClient reportClient) {
        routes.put("/api/orders",  ordersClient);
        routes.put("/api/catalog", catalogClient);
        routes.put("/api/audit",   auditClient);
        routes.put("/api/report",  reportClient);
    }

    @RequestMapping(value = {"/api/orders/**", "/api/catalog/**", "/api/audit/**", "/api/report/**"},
                    method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<String> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) String body) {

        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = query == null ? path : path + "?" + query;

        RestClient client = routes.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"RUTA_NO_MAPEADA\"}");
        }

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        log.info("[BFF] {} {} -> upstream", method, fullPath);

        var spec = client.method(method).uri(fullPath);
        if (auth != null) spec = spec.header(HttpHeaders.AUTHORIZATION, auth);
        if (body != null && !body.isBlank()) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        try {
            return spec.retrieve().toEntity(String.class);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(org.springframework.security.core.Authentication auth) {
        if (auth == null) return Map.of("authenticated", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authenticated", true);
        out.put("name", auth.getName());
        out.put("authorities", auth.getAuthorities().stream().map(Object::toString).toList());
        if (auth.getPrincipal() instanceof Jwt jwt) {
            out.put("roles", jwt.getClaimAsStringList("roles"));
            out.put("email", jwt.getClaimAsString("preferred_username"));
        }
        return out;
    }

    @GetMapping("/api/ping")
    public String ping() {
        return "pong - autenticado correctamente";
    }
}

@Configuration
@Profile("!local")
@EnableMethodSecurity
class BffSecurity {

    /** Origenes permitidos del frontend. En EC2 agrega aqui la URL publica. */
    @Value("${p360.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> c.configurationSource(cors()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/report/**").hasRole("ADMIN")
                .requestMatchers("/api/audit/**").hasAnyRole("ADMIN", "AUDITOR")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter())));
        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> converter() {
        return jwt -> {
            Collection<GrantedAuthority> auths = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
            return new JwtAuthenticationToken(jwt, auths);
        };
    }

    @Bean
    CorsConfigurationSource cors() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of(allowedOrigins));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}

@Configuration
@Profile("local")
class BffLocalSecurity {
    @Value("${p360.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> c.configurationSource(localCors()))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    private CorsConfigurationSource localCors() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of(allowedOrigins));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
