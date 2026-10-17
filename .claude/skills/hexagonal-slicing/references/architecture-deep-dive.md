# Architecture deep dive

This documents the reference project file by file so a new slice can be
written (by hand or read/adjusted after scaffolding) with the exact same
shape. Package root in the reference project is `com.example.hexagonal`,
feature `product`. Substitute your own base package and feature name.

## End-to-end request flow (create example)

```
HTTP POST /products {description, price}
  → ProductController.createProduct(@Valid ProductRequest)
      → ProductDtoMapper.toDomain(request)          // DTO -> domain model
      → ProductService.createProduct(Product)
          → CreateProductUseCase.createProduct(Product)   // impl: CreateProductUseCaseImpl
              → ProductRepository.create(Product)          // outbound port
                  → JpaProductRepositoryAdapter.create(Product)
                      → ProductMapper.toEntity(Product)     // domain -> JPA entity
                      → JpaProductRepository.save(ProductEntity)
                      → ProductMapper.toDomainModel(saved)  // JPA entity -> domain
      ← Product (domain, with generated id)
  ← ProductDtoMapper.toResponse(Product)            // domain -> DTO
  ← 201 Created, ProductResponse body
```

Notice: the JPA entity only ever exists inside
`infrastructure.persistence.*`. The web DTOs only ever exist inside
`infrastructure.web.*`. The domain model (`Product`) is the only type that
crosses layer boundaries within `application`.

## `domain/model/<Feature>.java`

Plain Lombok POJO. No JPA, no validation, no Jackson annotations — this class
must be usable in a pure unit test with zero Spring context.

```java
package com.example.hexagonal.product.domain.model;

import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long id;
    private String description;
    private BigDecimal price;
}
```

## `domain/ports/in/*.java` — inbound ports (use cases)

One interface per use case, named `<Verb><Feature>UseCase`. Each is
single-method — this is deliberate: it makes every unit of business capability
independently testable/mockable and keeps `ProductService` as the only place
that composes them. The standard CRUD set:

```java
public interface CreateProductUseCase { Product createProduct(Product product); }
public interface GetProductByIdUseCase { Optional<Product> getProductById(Long id); }
public interface GetAllProductsUseCase { List<Product> getAllProducts(); }
public interface UpdateProductUseCase { Optional<Product> updateProduct(Long id, Product product); }
public interface DeleteProductUseCase { void deleteProduct(Long id); }
```

`getById` and `update` return `Optional` — "not found" is not an exception at
this layer, it's a value. Deciding it's actually an error (and throwing) is
the `Service`'s job, not the use case's.

## `domain/ports/out/<Feature>Repository.java` — outbound port

What the domain needs from persistence, expressed in domain terms (no JPA
types anywhere):

```java
public interface ProductRepository {
    Product create(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAll();
    Optional<Product> update(Long id, Product product);
    boolean deleteById(Long id);
}
```

## `application/usecases/<Verb><Feature>UseCaseImpl.java`

`@Component @RequiredArgsConstructor`, implements exactly one inbound port,
depends only on the outbound port (`<Feature>Repository`). Thin — it just
delegates. Business rules that don't belong in the domain model itself go
here (e.g. "can't delete a product that's in an open order" would be a check
added inside `DeleteProductUseCaseImpl` before calling the repository).

```java
@Component
@RequiredArgsConstructor
public class CreateProductUseCaseImpl implements CreateProductUseCase {
    private final ProductRepository productRepository;

    @Override
    public Product createProduct(Product product) {
        return productRepository.create(product);
    }
}
```

## `application/service/<Feature>Service.java` — the facade

`@Service @RequiredArgsConstructor`. Injects every inbound port for the
feature and exposes plain methods the web layer calls. This is also where
"not found" becomes an exception, via `orElseThrow`:

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final CreateProductUseCase createProductUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final GetAllProductsUseCase getAllProductsUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    public Product createProduct(Product product) {
        return createProductUseCase.createProduct(product);
    }

    public Product getProductById(Long id) {
        return getProductByIdUseCase.getProductById(id)
                .orElseThrow(() -> new ProductException(id));
    }

    public List<Product> getAllProducts() { return getAllProductsUseCase.getAllProducts(); }

    public Product updateProduct(Long id, Product product) {
        return updateProductUseCase.updateProduct(id, product)
                .orElseThrow(() -> new ProductException(id));
    }

    public void deleteProduct(Long id) { deleteProductUseCase.deleteProduct(id); }
}
```

Why have both use cases *and* a service that mostly just calls them 1:1? The
use cases are the granular, independently swappable/testable units the
hexagon's "ports" formally represent; the service is the single injectable
facade the web layer (or any other inbound adapter — a CLI, a message
listener) needs, so controllers don't have to inject five separate use case
beans. When a feature grows real orchestration logic (e.g. "create product
then emit event then audit-log"), that logic lives in the service method, not
smeared across the controller.

## `application/exception/<Feature>Exception.java`

Plain `RuntimeException` subclass, one per feature, constructed from the id
that wasn't found:

```java
public class ProductException extends RuntimeException {
    public ProductException(Long id) {
        super("Could not find product with id: " + id);
    }
}
```

## `infrastructure/persistence/entity/<Feature>Entity.java`

The JPA-mapped twin of the domain model. Same fields, different annotations —
this duplication is intentional, it's what keeps `jakarta.persistence` out of
the domain.

```java
@Getter @Setter
@Entity
@Table(name = "products")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String description;
    private BigDecimal price;
}
```

## `infrastructure/persistence/mapper/<Feature>Mapper.java`

`@Component`, two pure functions, both null-guarded:

```java
@Component
public class ProductMapper {
    public ProductEntity toEntity(Product product) {
        if (product == null) return null;
        return ProductEntity.builder()
                .id(product.getId())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }

    public Product toDomainModel(ProductEntity entity) {
        if (entity == null) return null;
        return Product.builder()
                .id(entity.getId())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .build();
    }
}
```

## `infrastructure/persistence/repository/Jpa<Feature>Repository.java`

Plain Spring Data interface, no custom methods unless the feature needs a
derived query (add them here, e.g. `List<ProductEntity> findByDescriptionContaining(String q)`,
and expose through the outbound port + adapter, never expose `ProductEntity`
or this interface outside `infrastructure`):

```java
@Repository
public interface JpaProductRepository extends JpaRepository<ProductEntity, Long> {
}
```

## `infrastructure/persistence/adapter/Jpa<Feature>RepositoryAdapter.java`

`@Component @RequiredArgsConstructor`, implements the domain's outbound port.
This is the piece that would change if you swapped JPA for MongoDB, a REST
client, or an in-memory map — nothing above `infrastructure` would notice.

```java
@Component
@RequiredArgsConstructor
public class JpaProductRepositoryAdapter implements ProductRepository {
    private final JpaProductRepository jpaProductRepository;
    private final ProductMapper productMapper;

    @Override
    public Product create(Product product) {
        ProductEntity entity = productMapper.toEntity(product);
        return productMapper.toDomainModel(jpaProductRepository.save(entity));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaProductRepository.findById(id).map(productMapper::toDomainModel);
    }

    @Override
    public List<Product> findAll() {
        return jpaProductRepository.findAll().stream()
                .map(productMapper::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Product> update(Long id, Product product) {
        if (jpaProductRepository.existsById(id)) {
            ProductEntity entity = productMapper.toEntity(product);
            entity.setId(id); // ensure the id is preserved on update
            return Optional.of(productMapper.toDomainModel(jpaProductRepository.save(entity)));
        }
        return Optional.empty();
    }

    @Override
    public boolean deleteById(Long id) {
        if (jpaProductRepository.existsById(id)) {
            jpaProductRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
```

## `infrastructure/web/dto/<Feature>Request.java` / `<Feature>Response.java`

Request carries bean-validation annotations; Response is a flat mirror of the
domain model (including `id`, unlike Request). Both Lombok
`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`:

```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductRequest {
    @NotBlank(message = "Description is mandatory")
    private String description;

    @NotNull(message = "Price is mandatory")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;
}
```

```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String description;
    private BigDecimal price;
}
```

## `infrastructure/web/mapper/<Feature>DtoMapper.java`

`@Component`, DTO ⇄ domain, null-guarded, same shape as the persistence
mapper:

```java
@Component
public class ProductDtoMapper {
    public Product toDomain(ProductRequest request) {
        if (request == null) return null;
        return Product.builder()
                .description(request.getDescription())
                .price(request.getPrice())
                .build();
    }

    public ProductResponse toResponse(Product domain) {
        if (domain == null) return null;
        return ProductResponse.builder()
                .id(domain.getId())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .build();
    }
}
```

## `infrastructure/web/controller/<Feature>Controller.java`

`@RestController @RequestMapping("/<plural>") @RequiredArgsConstructor`.
Depends only on the `Service` and the DTO `Mapper`. Catches the feature's
`Exception` and converts it to a 404 via `ResponseStatusException` — this is
the only place HTTP status codes are decided.

```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductDtoMapper productDtoMapper;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        Product product = productDtoMapper.toDomain(productRequest);
        Product created = productService.createProduct(product);
        return new ResponseEntity<>(productDtoMapper.toResponse(created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productDtoMapper.toResponse(productService.getProductById(id)));
        } catch (ProductException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> responses = productService.getAllProducts().stream()
                .map(productDtoMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        Product product = productDtoMapper.toDomain(productRequest);
        try {
            return ResponseEntity.ok(productDtoMapper.toResponse(productService.updateProduct(id, product)));
        } catch (ProductException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Customizing a slice

- **Extra field**: add it to the domain model, the entity, both mappers, the
  Request/Response DTOs (with the right validation annotation), in that
  order. The scaffolder does all of this at generation time — for a
  post-generation addition, touch these six files by hand.
- **Extra query** (e.g. filter by a field): add the derived method to
  `Jpa<Feature>Repository`, add the matching method to the outbound port
  `<Feature>Repository`, implement it in the adapter, add a new inbound port
  + use case impl if it's a distinct capability, wire it into the `Service`,
  expose a new `@GetMapping` on the controller.
- **Cross-feature calls** (e.g. `OrderService` needs to check product stock):
  the calling feature's use case impl should depend on the other feature's
  **inbound port** (its use case interface), never reach into its
  `infrastructure` or `domain.model` internals directly beyond the shared
  domain model type. Keep the dependency one-directional to avoid a cycle
  between slices.
- **Swapping persistence tech**: only `infrastructure/persistence/*` changes.
  The domain, application, and web layers are untouched — that's the payoff
  of the outbound port.
- **Global exception handling**: if the project grows past one or two
  features, consider a `@RestControllerAdvice` in a shared/top-level package
  instead of repeating the try/catch in every controller method — mention
  this to the user as an option rather than doing it unprompted, since the
  reference project intentionally keeps it local per-controller.
