# ZillWrapper

## English
### Project Purpose
ZillWrapper is an operation orchestration backend. It ingests source events, authenticates actors, routes intents, executes staged operation pipelines, enriches external data, and delivers user-facing results through decoupled communication channels.

### Current Domain Profile
Current production profile is centered on order/license operations such as creation, generation, enrichment, and notification.  
The same `operation/source/channel` model is used across the platform and can be extended to additional business domains.

### Current Operator Features
- Structured Telegram order intake is implemented through `RegexOrderTelegramRouter`, `OrderTextParser`, `OrderReferenceLineParser`, and `OrderItemLineParser`.
- The first line can resolve an existing order reference or a free-text identifier. Numeric references are classified as `portalId` or `whiteAdminId` using configurable baselines and windows: `order.reference.portal-baseline`, `order.reference.portal-window`, `order.reference.white-admin-baseline`, and `order.reference.white-admin-window`.
- Preview, edit, confirm, duplicate questions, WA placeholder-order decisions, and cancel flows are implemented through `TelegramPreviewEditOrchestrator`, `PendingTaskService`, and `PendingTaskExecutor`.
- Live operation status is projected through `TelegramControlMessageService`: stage timeline, enrichment progress, warnings, final result, and cancel action for cancelable operations.
- Search is available through `/s` with shared selectors and filters such as `wzid`, `wid2`, `pid`, `woid`, `lex`, `kid`, `kon`, `kof`, `comment`, and product filter `-p`.
- Follow-up actions `/block`, `/allow`, `/detach`, and `/resend` reuse the same search/selector model and then execute as confirmed operations over the selected result set, including current/all confirmation flows in the interactive path.
- Order-message flags already supported in the parser/help flow include delivery/output flags (`-e`, `-ne`, `-t`), subscription flags (`-s`, `-uns`, `-sd`), client and partner flags (`-c`, `-p`, `-np`), locale override (`-l`), and subscription timing parameters (`-wl`, `-si`). Flag parsing is centralized in `FlagParser` and `ParameterFlagParser`; some flags explicitly support negative forms. See [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md).
- Enrichment menu supports tracked runs, cancel, `latest` / `-1` start mode, count-from-latest flow, and separate license/activation parallelism settings.
- Users can operate with different permissions and quotas depending on their role, source binding, and product access policy. See [docs/core-security-model.md](docs/core-security-model.md).

### API Status
Current API provides authenticated read access to orders and licenses through `src/main/java/com/zillya/timonfech/zillwrapper/api`.  
Roles `ADMIN`, `MANAGER`, and `LLM_READONLY` can use read endpoints. Each request returns between `1` and `500` records. `LLM_READONLY` uses masking sessions by default, while human masking access is optional and controlled by configuration. Generation and mutation endpoints are still under development.

### Core Architecture
- Routing and intent contract: `RoutingService`, `IntentRouter`, `RoutingDecision` in [docs/core-routing-and-intent.md](docs/core-routing-and-intent.md)
- Parsing and diagnostics contract: `IMatcher<T>`, `MatchingException`, `OrderTextParser` in [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md)
- Operation context and pipeline execution: `OrderOperationContext`, `PipelineDispatcher`, `OperationGraphRegistry`, `OperationLifecycleAspect` in [docs/core-pipeline.md](docs/core-pipeline.md)
- Questions, answers, and resume contract: `Question`, `Answer`, `ResumableOperationHandler`, `TelegramInteractionAnswerOrchestrator` in [docs/core-interactions-and-resume.md](docs/core-interactions-and-resume.md)
- Source ingestion and authentication: `InboundEventOrchestrator`, `InboundEvent`, `AuthenticationHandler`, `IdentityExtractor` in [docs/core-source-and-auth.md](docs/core-source-and-auth.md)
- Runtime and pending coordination: `OperationRuntimeRegistry`, `PendingTaskService`, `PendingTaskExecutor` in [docs/core-runtime-and-pending.md](docs/core-runtime-and-pending.md)
- Search and operator actions: `SearchQueryParser`, `UnifiedSearchService`, `CommandInteractionService`, `LicenseActionExecutor` in [docs/core-search-and-actions.md](docs/core-search-and-actions.md)
- Communication and UX projection: `TelegramControlMessageService`, `EmailNotifyService`, `EmailCommunicationService` in [docs/core-communication-and-ux.md](docs/core-communication-and-ux.md)
- Enrichment engine: `EnrichmentTaskManager`, `EnrichmentOrchestrator`, `EnrichmentActivationRuntimeService` in [docs/apis-enrichment.md](docs/apis-enrichment.md)

### Reliability and Recoverability
The system persists operation lifecycle and stage execution records, then coordinates progression through deterministic handlers and async completion bridges.  
This supports resumable execution and controlled recovery after interruptions.

### Extensibility
- Source-level extensibility is based on `InboundEvent`, `IntentRouter`, and source orchestrator contracts.
- Transport-level extensibility is based on routing and interaction abstractions, so Telegram-specific UX can be replaced by another channel adapter while preserving operation semantics.
- Artifact generation and notification are separate pipeline stages such as `LicenseArtifactGenerationHandler` and `NotifyHandler`.

### Security
Security model and runtime configuration guidance are documented in [docs/core-security-model.md](docs/core-security-model.md).

### Documentation Index
- [docs/tech-stack.md](docs/tech-stack.md)
- [docs/core-pipeline.md](docs/core-pipeline.md)
- [docs/core-routing-and-intent.md](docs/core-routing-and-intent.md)
- [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md)
- [docs/core-interactions-and-resume.md](docs/core-interactions-and-resume.md)
- [docs/core-search-and-actions.md](docs/core-search-and-actions.md)
- [docs/core-source-and-auth.md](docs/core-source-and-auth.md)
- [docs/core-runtime-and-pending.md](docs/core-runtime-and-pending.md)
- [docs/core-communication-and-ux.md](docs/core-communication-and-ux.md)
- [docs/apis-enrichment.md](docs/apis-enrichment.md)
- [docs/core-security-model.md](docs/core-security-model.md)
- [docs/api-roadmap.md](docs/api-roadmap.md)
- [OPERATION_ARCHITECTURE_NOTES.md](OPERATION_ARCHITECTURE_NOTES.md)

## Українська
### Мета проєкту
ZillWrapper — це backend-платформа оркестрації операцій. Вона приймає події від джерел, автентифікує акторів, маршрутизує наміри, виконує поетапні pipeline-операції, робить enrichment зовнішніх даних і доставляє результати через відокремлені канали комунікації.

### Поточний доменний профіль
Поточний production-профіль сфокусований на order/license сценаріях: створення, генерація, enrichment і notify.  
Та сама модель `operation/source/channel` використовується в платформі й може розширюватися на інші бізнес-домени.

### Поточні можливості для операторів
- Структурований прийом Telegram-замовлень реалізований через `RegexOrderTelegramRouter`, `OrderTextParser`, `OrderReferenceLineParser` і `OrderItemLineParser`.
- Перший рядок може визначати існуюче посилання на замовлення або текстовий identifier. Числові значення класифікуються як `portalId` або `whiteAdminId` через конфігуровані baselines і windows: `order.reference.portal-baseline`, `order.reference.portal-window`, `order.reference.white-admin-baseline` і `order.reference.white-admin-window`.
- Preview, edit, confirm, duplicate questions, рішення про створення WA placeholder order і cancel-flow реалізовані через `TelegramPreviewEditOrchestrator`, `PendingTaskService` і `PendingTaskExecutor`.
- Live-статус операцій відображається через `TelegramControlMessageService`: timeline стадій, enrichment progress, warnings, final result і cancel action для cancelable-операцій.
- Пошук доступний через `/s` зі спільними селекторами та фільтрами: `wzid`, `wid2`, `pid`, `woid`, `lex`, `kid`, `kon`, `kof`, `comment` і product filter `-p`.
- Дії `/block`, `/allow`, `/detach` і `/resend` наслідують ту саму модель пошуку та селекторів, а далі виконуються як підтверджувані операції над вибраним набором результатів, зокрема через interactive current/all confirm flow.
- У парсері та help-flow вже підтримуються прапори замовлення: delivery/output (`-e`, `-ne`, `-t`), subscription (`-s`, `-uns`, `-sd`), client і partner (`-c`, `-p`, `-np`), locale override (`-l`) і параметри часу підписки (`-wl`, `-si`). Парсинг прапорів централізований у `FlagParser` і `ParameterFlagParser`; частина прапорів має explicit negative-форми. Детальніше: [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md).
- Меню enrichment підтримує tracked runs, cancel, режим старту `latest` / `-1`, сценарій count-from-latest і окремі налаштування паралелізму для ліцензій та активацій.
- Різні користувачі можуть мати різні права та квоти залежно від ролі, source binding і product access policy. Детальніше: [docs/core-security-model.md](docs/core-security-model.md).

### API
Поточний API надає автентифікований read-access до orders і licenses через `src/main/java/com/zillya/timonfech/zillwrapper/api`.  
Ролі `ADMIN`, `MANAGER` і `LLM_READONLY` мають доступ до read endpoints. Один запит повертає від `1` до `500` записів. Для `LLM_READONLY` masking sessions увімкнені за замовчуванням, а для human API users вони доступні лише через окрему конфігурацію. Endpoints для генерації та mutation-операцій ще розробляються.

### Основна архітектура
- Контракт роутингу й намірів: `RoutingService`, `IntentRouter`, `RoutingDecision` у [docs/core-routing-and-intent.md](docs/core-routing-and-intent.md)
- Контракт парсингу та діагностики: `IMatcher<T>`, `MatchingException`, `OrderTextParser` у [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md)
- Операційний контекст і виконання pipeline: `OrderOperationContext`, `PipelineDispatcher`, `OperationGraphRegistry`, `OperationLifecycleAspect` у [docs/core-pipeline.md](docs/core-pipeline.md)
- Контракт questions, answers і resume: `Question`, `Answer`, `ResumableOperationHandler`, `TelegramInteractionAnswerOrchestrator` у [docs/core-interactions-and-resume.md](docs/core-interactions-and-resume.md)
- Прийом джерел і автентифікація: `InboundEventOrchestrator`, `InboundEvent`, `AuthenticationHandler`, `IdentityExtractor` у [docs/core-source-and-auth.md](docs/core-source-and-auth.md)
- Runtime і координація pending-задач: `OperationRuntimeRegistry`, `PendingTaskService`, `PendingTaskExecutor` у [docs/core-runtime-and-pending.md](docs/core-runtime-and-pending.md)
- Пошук і операторські дії: `SearchQueryParser`, `UnifiedSearchService`, `CommandInteractionService`, `LicenseActionExecutor` у [docs/core-search-and-actions.md](docs/core-search-and-actions.md)
- Комунікація та UX-проєкція: `TelegramControlMessageService`, `EmailNotifyService`, `EmailCommunicationService` у [docs/core-communication-and-ux.md](docs/core-communication-and-ux.md)
- Enrichment engine: `EnrichmentTaskManager`, `EnrichmentOrchestrator`, `EnrichmentActivationRuntimeService` у [docs/apis-enrichment.md](docs/apis-enrichment.md)

### Надійність і відновлюваність
Система зберігає життєвий цикл операцій і записи виконання стадій, а потім координує прогрес через детерміновані handler-и та async completion bridges.  
Це забезпечує відновлюване виконання і контрольоване відновлення після переривань.

### Розширюваність
- Розширення на рівні джерел базується на контрактах `InboundEvent`, `IntentRouter` і source orchestrator.
- Розширення на рівні транспорту базується на routing та interaction-абстракціях, тому Telegram-UX можна замінити іншим адаптером каналу без зміни семантики операцій.
- Генерація артефактів і notify винесені в окремі pipeline stages, зокрема `LicenseArtifactGenerationHandler` і `NotifyHandler`.

### Безпека
Модель безпеки та вимоги до runtime-конфігурації описані в [docs/core-security-model.md](docs/core-security-model.md).

### Індекс документації
- [docs/tech-stack.md](docs/tech-stack.md)
- [docs/core-pipeline.md](docs/core-pipeline.md)
- [docs/core-routing-and-intent.md](docs/core-routing-and-intent.md)
- [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md)
- [docs/core-interactions-and-resume.md](docs/core-interactions-and-resume.md)
- [docs/core-search-and-actions.md](docs/core-search-and-actions.md)
- [docs/core-source-and-auth.md](docs/core-source-and-auth.md)
- [docs/core-runtime-and-pending.md](docs/core-runtime-and-pending.md)
- [docs/core-communication-and-ux.md](docs/core-communication-and-ux.md)
- [docs/apis-enrichment.md](docs/apis-enrichment.md)
- [docs/core-security-model.md](docs/core-security-model.md)
- [docs/api-roadmap.md](docs/api-roadmap.md)
- [OPERATION_ARCHITECTURE_NOTES.md](OPERATION_ARCHITECTURE_NOTES.md)
