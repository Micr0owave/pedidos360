package cl.duoc.p360.catalog;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class CatalogApplication {
    public static void main(String[] args) { SpringApplication.run(CatalogApplication.class, args); }

    @Bean
    CommandLineRunner seed(ProductRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Product("P-001", "Pizza Napolitana", 8990L, 50));
                repo.save(new Product("P-002", "Hamburguesa Doble", 7490L, 40));
                repo.save(new Product("P-003", "Ensalada Cesar",    5990L, 30));
                repo.save(new Product("P-004", "Bebida 500ml",      1500L, 200));
            }
        };
    }
}

@Entity
@Table(name = "P360_PRODUCTS")
class Product {
    @Id @Column(length = 50) private String id;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false) private Long price;
    @Column(nullable = false) private Integer stock;

    protected Product() {}
    Product(String id, String name, Long price, Integer stock) {
        this.id = id; this.name = name; this.price = price; this.stock = stock;
    }
    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Long getPrice() { return price; }
    public void setPrice(Long v) { this.price = v; }
    public Integer getStock() { return stock; }
    public void setStock(Integer v) { this.stock = v; }
}

interface ProductRepository extends JpaRepository<Product, String> {}

record ProductRequest(@NotBlank String id, @NotBlank String name,
                      @NotNull @Min(0) Long price, @NotNull @Min(0) Integer stock) {}
record StockChange(@NotNull @Min(1) Integer quantity) {}

@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalogo", description = "CRUD de productos y control de stock")
class ProductController {

    private final ProductRepository repo;
    ProductController(ProductRepository repo) { this.repo = repo; }

    @GetMapping
    public List<Product> list() { return repo.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable String id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody ProductRequest r) {
        return repo.save(new Product(r.id(), r.name(), r.price(), r.stock()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable String id, @RequestBody ProductRequest r) {
        return repo.findById(id).map(p -> {
            p.setName(r.name()); p.setPrice(r.price()); p.setStock(r.stock());
            return ResponseEntity.ok(repo.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Lo llama orders al ACEPTAR un pedido: el stock decrece en ese momento. */
    @PostMapping("/{id}/stock/decrease")
    @Transactional
    public ResponseEntity<?> decrease(@PathVariable String id, @RequestBody StockChange body) {
        return repo.findById(id).map(p -> {
            if (p.getStock() < body.quantity()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "STOCK_INSUFICIENTE",
                                     "disponible", p.getStock(),
                                     "solicitado", body.quantity()));
            }
            p.setStock(p.getStock() - body.quantity());
            return ResponseEntity.ok((Object) repo.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }
}

@Configuration
@Profile("!local")
@EnableMethodSecurity
class CatalogSecurity {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(org.springframework.security.config.Customizer.withDefaults()));
        return http.build();
    }
}

@Configuration
@Profile("local")
class CatalogLocalSecurity {
    @Bean
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
