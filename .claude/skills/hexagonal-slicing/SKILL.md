---
name: hexagonal-slicing
description: Scaffold and extend Java Spring Boot backends using hexagonal architecture (ports & adapters) organized by vertical slices (one self-contained package per business feature/entity, e.g. product, order, customer), following this exact stack and layout — Spring Boot 4.x parent, Java 25, Lombok, Spring Data JPA, H2, springdoc-openapi, one-interface-per-use-case ports, a facade Service, JPA entity/mapper/adapter persistence, and a REST controller with request/response DTOs. Use this whenever the user asks to create a new Spring Boot project "con arquitectura hexagonal", "hexagonal architecture", "ports and adapters", "vertical slice architecture", wants a new backend feature/entity/CRUD/resource added following hexagonal conventions, or references "el proyecto hexagonal" / a hexagonal template project as the pattern to replicate. Also use when reviewing or refactoring existing Java backend code toward hexagonal/ports-and-adapters structure, or when the user wants a use case, port, adapter, or feature slice added to an existing hexagonal project. Triggers even if the user doesn't say "skill" explicitly — e.g. "agregá una entidad Order con arquitectura hexagonal" or "necesito un backend hexagonal para clientes".
---

# Hexagonal Architecture — Vertical Slicing (Spring Boot)

This skill packages the exact architecture, package layout, stack, and file-level
conventions of a reference Spring Boot project ("hexagonal") so new projects or
new features reproduce it faithfully — same packages, same annotations, same
naming, same responsibilities per layer.

## What this architecture is

**Hexagonal (ports & adapters)** with **vertical slicing**: instead of slicing the
codebase horizontally by technical layer at the top level (`controllers/`,
`services/`, `repositories/`), each **business feature/entity** (`product`,
`order`, `customer`, ...) is a **self-contained package** that internally follows
the three hexagonal layers:

```
com.example.<app>.<feature>/
├── domain/            ← pure business logic, zero framework dependencies
│   ├── model/          the domain object (no JPA/Jackson annotations)
│   └── ports/
│       ├── in/          one interface per use case ("what the app can do")
│       └── out/         interfaces the domain needs from the outside (repository)
├── application/       ← orchestrates the domain, implements the ports
│   ├── usecases/        one impl class per inbound port, @Component
│   ├── service/         facade that composes use cases, @Service — this is what
│   │                    the web layer talks to
│   └── exception/       domain/application-level exceptions
└── infrastructure/    ← adapters: framework, DB, HTTP — everything replaceable
    ├── persistence/
    │   ├── entity/       JPA @Entity, separate from the domain model
    │   ├── repository/   Spring Data JpaRepository interface
    │   ├── mapper/        entity ⇄ domain model
    │   └── adapter/       implements the domain's outbound port using JPA
    └── web/
        ├── controller/   @RestController, talks only to the Service + domain model
        ├── dto/           Request/Response DTOs with bean validation
        └── mapper/        DTO ⇄ domain model
```

**Dependency rule (the whole point of hexagonal):** dependencies point inward.
`domain` depends on nothing else in the feature. `application` depends only on
`domain`. `infrastructure` depends on `application` and `domain`, never the
reverse. The JPA `@Entity` and the web DTOs never leak outside `infrastructure` —
the domain model is what flows through `application` and is what the controller
receives/returns after mapping.

Read `references/architecture-deep-dive.md` before writing code by hand — it
documents every file's exact annotations, responsibilities, and the end-to-end
request flow (HTTP → controller → service → use case → port → adapter → JPA →
back), plus the rules that keep the layers honest (e.g. why `update` and
`getById` return `Optional`, why use cases are single-method interfaces, why the
mapper classes guard against `null`).

## Stack (reproduce exactly unless the user says otherwise)

- **Spring Boot** `4.1.0` (as the Maven parent), **Java 25**
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`
- `springdoc-openapi-starter-webmvc-ui` `3.1.0` (Swagger UI for free)
- `com.h2database:h2` (`runtime` scope) — in-memory DB, zero setup, swap later for Postgres/MySQL by changing only `application.properties` and the JDBC driver dependency
- `org.projectlombok:lombok` (`optional`) — `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` on domain models, entities, and DTOs; `@RequiredArgsConstructor` for constructor injection everywhere (never field injection with `@Autowired`)
- `spring-boot-starter-test` (`test` scope)
- `org.apache.commons:commons-lang3` pinned in `<dependencyManagement>` (available if a feature needs it, not a hard dependency of the base template)

Full `pom.xml` template, `application.properties`, and the `@SpringBootApplication`
main class are in `references/project-bootstrap.md` — use it verbatim when
starting a brand-new project.

## Workflow

### 1. Starting a brand-new project

If there's no existing Spring Boot project yet:

1. Read `references/project-bootstrap.md` and create `pom.xml`,
   `src/main/resources/application.properties`, and the
   `<Name>Application.java` main class exactly as templated there (swap the
   base package/artifact name for the user's app name).
2. For each initial feature/entity the user wants, run the scaffolder (step 2).
3. Build with `./mvnw -q compile` (generate the wrapper with
   `mvn -N io.takari:maven:wrapper` first if `mvnw` is missing) to confirm it
   compiles before handing back to the user.

### 2. Adding a feature (vertical slice) — always use the scaffolder script

Do **not** hand-write the ~20 boilerplate files of a slice one by one. Use
`scripts/scaffold_feature.py`, which generates every file for a full CRUD
vertical slice — domain model, 5 inbound ports, outbound port, 5 use case
impls, service facade, exception, JPA entity, persistence mapper, Spring Data
repository, persistence adapter, request/response DTOs, DTO mapper, and REST
controller — with the exact annotations, method names, and package layout of
the reference project.

```bash
python3 <skill_dir>/scripts/scaffold_feature.py \
  --base-package com.example.hexagonal \
  --feature Product \
  --fields "description:String,price:BigDecimal" \
  --src-root src/main/java \
  --route /products
```

- `--feature` is the singular PascalCase entity name (`Product`, `Order`,
  `Customer`). The script derives the plural for `GetAll...UseCase` names
  (naive English pluralization — override with `--plural` for irregular nouns,
  e.g. `--feature Category --plural Categories`).
- `--fields` is a comma-separated `name:JavaType` list (the `id: Long` field is
  added automatically, do not include it). Supported types get the right
  import and the right bean-validation annotation on the Request DTO
  automatically: `String` → `@NotBlank`, numeric types (`BigDecimal`,
  `Integer`, `Long`, `Double`, `int`, `long`, `double`) → `@NotNull`, anything
  else → `@NotNull` as a safe default (adjust by hand if the field needs
  something more specific, e.g. `@Email`, `@Size`).
- `--route` is the base REST path (defaults to `/<feature-lowercase-plural>`).
- `--src-root` defaults to `src/main/java`; run the command from the project
  root (or pass an absolute `--src-root`).
- Re-run with `--dry-run` first if you want to preview the file list without
  writing anything.

After scaffolding, open `references/architecture-deep-dive.md`'s "customizing a
slice" section for how to add fields, validation, extra endpoints (e.g.
search/filter query methods), or extra use cases beyond plain CRUD without
breaking the dependency rule.

### 3. Extending an existing slice

- **New use case** (e.g. `ActivateProductUseCase`): add the inbound port in
  `domain/ports/in/`, its impl in `application/usecases/` (`@Component`,
  `@RequiredArgsConstructor`, depends only on the outbound port), wire it into
  the feature's `Service` facade as a new constructor-injected field + method,
  expose it from the `Controller` if it needs an HTTP entry point.
- **New outbound dependency** (e.g. calling another service, sending an
  event): add the interface to `domain/ports/out/`, implement it in
  `infrastructure/` under a new adapter package (`infrastructure/messaging/adapter/`,
  `infrastructure/client/adapter/`, etc. — mirror the `persistence` pattern),
  inject it into the use case impl(s) that need it.
- Never let a JPA `@Entity` or a web DTO type appear in a `domain` or
  `application` class signature. If you catch yourself importing
  `infrastructure.*` from `domain` or `application`, that's the dependency
  rule broken — fix it by mapping at the boundary instead.

## Quick sanity checklist before calling a slice "done"

- [ ] Domain model has **no** `jakarta.persistence` or `jakarta.validation` or
      Jackson annotations — it's a plain Lombok POJO.
- [ ] Every inbound port is a **single-purpose interface** (one method), named
      `<Verb><Feature>UseCase` (`CreateProductUseCase`, not a fat
      `ProductUseCase` with five methods).
- [ ] Use case impls are `@Component`, the feature facade is `@Service`; both
      use `@RequiredArgsConstructor` constructor injection, never
      `@Autowired` fields.
- [ ] The outbound port (`<Feature>Repository` in `domain/ports/out/`) is
      implemented by a `@Component` adapter in
      `infrastructure/persistence/adapter/`, which delegates to a
      `JpaRepository` and maps through a dedicated mapper — the adapter itself
      never returns a JPA entity.
- [ ] Not-found cases return `Optional<Feature>` from the port/use case/repo
      chain; the `Service` facade is what turns an empty `Optional` into the
      feature's custom `RuntimeException`; the `Controller` catches that and
      maps it to `ResponseStatusException(HttpStatus.NOT_FOUND)`.
- [ ] Controller depends on the `Service` + the web `Mapper`, never on a use
      case or the outbound port directly, and never touches the JPA entity.
- [ ] Mappers (`persistence/mapper` and `web/mapper`) are plain `@Component`
      classes (no MapStruct in this template) and null-guard their inputs.
