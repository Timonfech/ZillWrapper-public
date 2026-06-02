# Tech Stack

## Runtime and language
- Java 22
- Spring Boot 4.x
- Maven build (`spring-boot-maven-plugin`, `maven-source-plugin`)

## Core framework modules
- `spring-boot-starter-webmvc`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `spring-boot-starter-mail`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-restclient`
- `spring-boot-starter-flyway`

## Persistence and migrations
- PostgreSQL (runtime primary)
- H2 (runtime/test support)
- Flyway migrations (`db/migration`)
- JPA/Hibernate entities (for example `OperationExecutionEntity`, `LicenseEntity`, `OrderEntity`)

## Integration and parsing
- Telegram Bot API via `telegrambots-spring-boot-starter`
- JSoup for HTML parsing of external admin pages
- Jackson (`tools.jackson` packages in project) for serialization and JSON payload fields

## Artifacts and rendering
- Apache POI (`poi`, `poi-ooxml`) for spreadsheet artifact generation
- Thymeleaf + MessageSource for email template rendering and i18n

## Concurrency model (current implementation)
- Virtual-thread-based executors configured in `TaskExecutorConfig`
- Async pipeline progression through `PipelineDispatcher` + `AsyncStageCoordinator`
- Runtime enrichment orchestration in `EnrichmentTaskManager` and `EnrichmentActivationRuntimeService`

## Test stack
- Spring Boot test starters (`webmvc-test`, `security-test`, `restclient-test`, `thymeleaf-test`, `flyway-test`)
- Datafaker for synthetic fixture data

## Related docs
- [core-pipeline.md](core-pipeline.md)
- [core-runtime-and-pending.md](core-runtime-and-pending.md)
- [apis-enrichment.md](apis-enrichment.md)
