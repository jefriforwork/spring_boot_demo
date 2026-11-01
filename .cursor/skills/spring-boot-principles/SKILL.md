---
name: spring-boot-principles
description: >-
  Write Spring Boot code in this repo as thin controllers, transactional
  services, Spring Data repositories, and validated DTOs. Use when adding or
  editing a controller, service, repository, entity, DTO, exception handler,
  Flyway migration, or application.properties.
---

# Spring Boot principles

Apply this when adding or changing code under `src/main/java` or `src/main/resources`. Match the existing packages. This repo is layered, not packaged by feature. Do not move classes into feature packages. HTTP paths start with `/api/v1`, as in `@RequestMapping("/api/v1/books")`.

## Layers

| Layer | Where | Holds |
| --- | --- | --- |
| HTTP | `controller/` | Mapping, `@Valid`, status. One service call |
| Use case | `service/` | Rules, `@Transactional`, mapping to a response |
| Persistence | `repository/` | Spring Data queries and `@EntityGraph` |
| Stored shape | `entity/` | JPA mapping. No HTTP types |
| Contract | `dto/` | Request and response types. No JPA annotations |
| Errors | `exception/` | Domain exceptions and `GlobalExceptionHandler` |
| Schema | `db/migration/` | Flyway SQL. Hibernate only validates |

Dependencies point one way: controller to service to repository. A controller does not inject a repository. A repository does not call a service. A service does not return `ResponseEntity` or take `HttpServletRequest`.

## Injection

Dependencies are `final` fields set in the constructor, as in `BookController` and `BookService`. Do not use field or setter `@Autowired`. A single constructor needs no `@Autowired`.

A `@Service`, `@RestController`, or `@Component` holds no field that changes per request.

Do not add an interface for a service that has one implementation.

## HTTP boundary

A request body is a type in `dto/` with Jakarta constraints, like `BookRequest`. The controller marks it `@Valid`. The service does not repeat those checks.

Return a type from `dto/`. Do not return the entity, and do not return `Map<String, Object>`. Map the entity inside the service method, while the transaction is still open. `BookResponse.from(book)` is the resource. A single resource is wrapped in `ReadResponse` so `dataSource` stays visible. A book list is `BookPageResponse`. An author list is `AuthorPageResponse`. Error bodies are `ErrorResponse` or `ValidationErrorResponse`. Do not hide fields with `@JsonIgnore` on the entity.

A missing row throws `ResourceNotFoundException`. A conflict throws a domain exception such as `DuplicateBookNameException`. Add the status in `GlobalExceptionHandler`. Do not catch these in the controller. Keep the existing body shape: `status` plus `message` or `errors`.

A list takes `Pageable`, as `getAllBooks` does. Do not add an unpaged `findAll` endpoint.

## Transactions and entities

`@Transactional` stays on the service. Reads use `@Transactional(readOnly = true)`. Writes use `@Transactional`. Do not put it on a controller or a repository.

Associations stay `FetchType.LAZY`, as on `Book.author`. If a response reads an association, the repository method declares `@EntityGraph`. Do not switch the association to `EAGER`, and do not turn `spring.jpa.open-in-view` on.

`spring.jpa.hibernate.ddl-auto` stays `validate`. A schema change is the next `V{n}__*.sql` under `src/main/resources/db/migration`. A new `NOT NULL` column needs a default or a backfill in that script.

## Shape

```java
@PostMapping
public ReadResponse<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
    return bookService.createBook(request);
}
```

```java
@Transactional
public ReadResponse<BookResponse> createBook(BookRequest request) {
    if (bookRepository.existsByName(request.getName())) {
        throw new DuplicateBookNameException(request.getName());
    }
    Author author = authorRepository.findById(request.getAuthorId())
            .orElseThrow(() -> new ResourceNotFoundException("Author", request.getAuthorId()));
    Book book = bookRepository.save(new Book(request.getName(), author));
    return new ReadResponse<>("primary (port 5432)", BookResponse.from(book));
}
```
