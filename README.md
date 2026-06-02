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

## Ð£ÐºÑ€Ð°Ñ—Ð½ÑÑŒÐºÐ°
### ÐœÐµÑ‚Ð° Ð¿Ñ€Ð¾Ñ”ÐºÑ‚Ñƒ
ZillWrapper â€” Ñ†Ðµ backend-Ð¿Ð»Ð°Ñ‚Ñ„Ð¾Ñ€Ð¼Ð° Ð¾Ñ€ÐºÐµÑÑ‚Ñ€Ð°Ñ†Ñ–Ñ— Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹. Ð’Ð¾Ð½Ð° Ð¿Ñ€Ð¸Ð¹Ð¼Ð°Ñ” Ð¿Ð¾Ð´Ñ–Ñ— Ð²Ñ–Ð´ Ð´Ð¶ÐµÑ€ÐµÐ», Ð°Ð²Ñ‚ÐµÐ½Ñ‚Ð¸Ñ„Ñ–ÐºÑƒÑ” Ð°ÐºÑ‚Ð¾Ñ€Ñ–Ð², Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚Ð¸Ð·ÑƒÑ” Ð½Ð°Ð¼Ñ–Ñ€Ð¸, Ð²Ð¸ÐºÐ¾Ð½ÑƒÑ” Ð¿Ð¾ÐµÑ‚Ð°Ð¿Ð½Ñ– pipeline-Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ñ—, Ñ€Ð¾Ð±Ð¸Ñ‚ÑŒ enrichment Ð·Ð¾Ð²Ð½Ñ–ÑˆÐ½Ñ–Ñ… Ð´Ð°Ð½Ð¸Ñ… Ñ– Ð´Ð¾ÑÑ‚Ð°Ð²Ð»ÑÑ” Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð¸ Ñ‡ÐµÑ€ÐµÐ· Ð²Ñ–Ð´Ð¾ÐºÑ€ÐµÐ¼Ð»ÐµÐ½Ñ– ÐºÐ°Ð½Ð°Ð»Ð¸ ÐºÐ¾Ð¼ÑƒÐ½Ñ–ÐºÐ°Ñ†Ñ–Ñ—.

### ÐŸÐ¾Ñ‚Ð¾Ñ‡Ð½Ð¸Ð¹ Ð´Ð¾Ð¼ÐµÐ½Ð½Ð¸Ð¹ Ð¿Ñ€Ð¾Ñ„Ñ–Ð»ÑŒ
ÐŸÐ¾Ñ‚Ð¾Ñ‡Ð½Ð¸Ð¹ production-Ð¿Ñ€Ð¾Ñ„Ñ–Ð»ÑŒ ÑÑ„Ð¾ÐºÑƒÑÐ¾Ð²Ð°Ð½Ð¸Ð¹ Ð½Ð° order/license ÑÑ†ÐµÐ½Ð°Ñ€Ñ–ÑÑ…: ÑÑ‚Ð²Ð¾Ñ€ÐµÐ½Ð½Ñ, Ð³ÐµÐ½ÐµÑ€Ð°Ñ†Ñ–Ñ, enrichment Ñ– notify.  
Ð¢Ð° ÑÐ°Ð¼Ð° Ð¼Ð¾Ð´ÐµÐ»ÑŒ `operation/source/channel` Ð²Ð¸ÐºÐ¾Ñ€Ð¸ÑÑ‚Ð¾Ð²ÑƒÑ”Ñ‚ÑŒÑÑ Ð² Ð¿Ð»Ð°Ñ‚Ñ„Ð¾Ñ€Ð¼Ñ– Ð¹ Ð¼Ð¾Ð¶Ðµ Ñ€Ð¾Ð·ÑˆÐ¸Ñ€ÑŽÐ²Ð°Ñ‚Ð¸ÑÑ Ð½Ð° Ñ–Ð½ÑˆÑ– Ð±Ñ–Ð·Ð½ÐµÑ-Ð´Ð¾Ð¼ÐµÐ½Ð¸.

### ÐŸÐ¾Ñ‚Ð¾Ñ‡Ð½Ñ– Ð¼Ð¾Ð¶Ð»Ð¸Ð²Ð¾ÑÑ‚Ñ– Ð´Ð»Ñ Ð¾Ð¿ÐµÑ€Ð°Ñ‚Ð¾Ñ€Ñ–Ð²
- Ð¡Ñ‚Ñ€ÑƒÐºÑ‚ÑƒÑ€Ð¾Ð²Ð°Ð½Ð¸Ð¹ Ð¿Ñ€Ð¸Ð¹Ð¾Ð¼ Telegram-Ð·Ð°Ð¼Ð¾Ð²Ð»ÐµÐ½ÑŒ Ñ€ÐµÐ°Ð»Ñ–Ð·Ð¾Ð²Ð°Ð½Ð¸Ð¹ Ñ‡ÐµÑ€ÐµÐ· `RegexOrderTelegramRouter`, `OrderTextParser`, `OrderReferenceLineParser` Ñ– `OrderItemLineParser`.
- ÐŸÐµÑ€ÑˆÐ¸Ð¹ Ñ€ÑÐ´Ð¾Ðº Ð¼Ð¾Ð¶Ðµ Ð²Ð¸Ð·Ð½Ð°Ñ‡Ð°Ñ‚Ð¸ Ñ–ÑÐ½ÑƒÑŽÑ‡Ðµ Ð¿Ð¾ÑÐ¸Ð»Ð°Ð½Ð½Ñ Ð½Ð° Ð·Ð°Ð¼Ð¾Ð²Ð»ÐµÐ½Ð½Ñ Ð°Ð±Ð¾ Ñ‚ÐµÐºÑÑ‚Ð¾Ð²Ð¸Ð¹ identifier. Ð§Ð¸ÑÐ»Ð¾Ð²Ñ– Ð·Ð½Ð°Ñ‡ÐµÐ½Ð½Ñ ÐºÐ»Ð°ÑÐ¸Ñ„Ñ–ÐºÑƒÑŽÑ‚ÑŒÑÑ ÑÐº `portalId` Ð°Ð±Ð¾ `whiteAdminId` Ñ‡ÐµÑ€ÐµÐ· ÐºÐ¾Ð½Ñ„Ñ–Ð³ÑƒÑ€Ð¾Ð²Ð°Ð½Ñ– baselines Ñ– windows: `order.reference.portal-baseline`, `order.reference.portal-window`, `order.reference.white-admin-baseline` Ñ– `order.reference.white-admin-window`.
- Preview, edit, confirm, duplicate questions, Ñ€Ñ–ÑˆÐµÐ½Ð½Ñ Ð¿Ñ€Ð¾ ÑÑ‚Ð²Ð¾Ñ€ÐµÐ½Ð½Ñ WA placeholder order Ñ– cancel-flow Ñ€ÐµÐ°Ð»Ñ–Ð·Ð¾Ð²Ð°Ð½Ñ– Ñ‡ÐµÑ€ÐµÐ· `TelegramPreviewEditOrchestrator`, `PendingTaskService` Ñ– `PendingTaskExecutor`.
- Live-ÑÑ‚Ð°Ñ‚ÑƒÑ Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹ Ð²Ñ–Ð´Ð¾Ð±Ñ€Ð°Ð¶Ð°Ñ”Ñ‚ÑŒÑÑ Ñ‡ÐµÑ€ÐµÐ· `TelegramControlMessageService`: timeline ÑÑ‚Ð°Ð´Ñ–Ð¹, enrichment progress, warnings, final result Ñ– cancel action Ð´Ð»Ñ cancelable-Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹.
- ÐŸÐ¾ÑˆÑƒÐº Ð´Ð¾ÑÑ‚ÑƒÐ¿Ð½Ð¸Ð¹ Ñ‡ÐµÑ€ÐµÐ· `/s` Ð·Ñ– ÑÐ¿Ñ–Ð»ÑŒÐ½Ð¸Ð¼Ð¸ ÑÐµÐ»ÐµÐºÑ‚Ð¾Ñ€Ð°Ð¼Ð¸ Ñ‚Ð° Ñ„Ñ–Ð»ÑŒÑ‚Ñ€Ð°Ð¼Ð¸: `wzid`, `wid2`, `pid`, `woid`, `lex`, `kid`, `kon`, `kof`, `comment` Ñ– product filter `-p`.
- Ð”Ñ–Ñ— `/block`, `/allow`, `/detach` Ñ– `/resend` Ð½Ð°ÑÐ»Ñ–Ð´ÑƒÑŽÑ‚ÑŒ Ñ‚Ñƒ ÑÐ°Ð¼Ñƒ Ð¼Ð¾Ð´ÐµÐ»ÑŒ Ð¿Ð¾ÑˆÑƒÐºÑƒ Ñ‚Ð° ÑÐµÐ»ÐµÐºÑ‚Ð¾Ñ€Ñ–Ð², Ð° Ð´Ð°Ð»Ñ– Ð²Ð¸ÐºÐ¾Ð½ÑƒÑŽÑ‚ÑŒÑÑ ÑÐº Ð¿Ñ–Ð´Ñ‚Ð²ÐµÑ€Ð´Ð¶ÑƒÐ²Ð°Ð½Ñ– Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ñ— Ð½Ð°Ð´ Ð²Ð¸Ð±Ñ€Ð°Ð½Ð¸Ð¼ Ð½Ð°Ð±Ð¾Ñ€Ð¾Ð¼ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ñ–Ð², Ð·Ð¾ÐºÑ€ÐµÐ¼Ð° Ñ‡ÐµÑ€ÐµÐ· interactive current/all confirm flow.
- Ð£ Ð¿Ð°Ñ€ÑÐµÑ€Ñ– Ñ‚Ð° help-flow Ð²Ð¶Ðµ Ð¿Ñ–Ð´Ñ‚Ñ€Ð¸Ð¼ÑƒÑŽÑ‚ÑŒÑÑ Ð¿Ñ€Ð°Ð¿Ð¾Ñ€Ð¸ Ð·Ð°Ð¼Ð¾Ð²Ð»ÐµÐ½Ð½Ñ: delivery/output (`-e`, `-ne`, `-t`), subscription (`-s`, `-uns`, `-sd`), client Ñ– partner (`-c`, `-p`, `-np`), locale override (`-l`) Ñ– Ð¿Ð°Ñ€Ð°Ð¼ÐµÑ‚Ñ€Ð¸ Ñ‡Ð°ÑÑƒ Ð¿Ñ–Ð´Ð¿Ð¸ÑÐºÐ¸ (`-wl`, `-si`). ÐŸÐ°Ñ€ÑÐ¸Ð½Ð³ Ð¿Ñ€Ð°Ð¿Ð¾Ñ€Ñ–Ð² Ñ†ÐµÐ½Ñ‚Ñ€Ð°Ð»Ñ–Ð·Ð¾Ð²Ð°Ð½Ð¸Ð¹ Ñƒ `FlagParser` Ñ– `ParameterFlagParser`; Ñ‡Ð°ÑÑ‚Ð¸Ð½Ð° Ð¿Ñ€Ð°Ð¿Ð¾Ñ€Ñ–Ð² Ð¼Ð°Ñ” explicit negative-Ñ„Ð¾Ñ€Ð¼Ð¸. Ð”ÐµÑ‚Ð°Ð»ÑŒÐ½Ñ–ÑˆÐµ: [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md).
- ÐœÐµÐ½ÑŽ enrichment Ð¿Ñ–Ð´Ñ‚Ñ€Ð¸Ð¼ÑƒÑ” tracked runs, cancel, Ñ€ÐµÐ¶Ð¸Ð¼ ÑÑ‚Ð°Ñ€Ñ‚Ñƒ `latest` / `-1`, ÑÑ†ÐµÐ½Ð°Ñ€Ñ–Ð¹ count-from-latest Ñ– Ð¾ÐºÑ€ÐµÐ¼Ñ– Ð½Ð°Ð»Ð°ÑˆÑ‚ÑƒÐ²Ð°Ð½Ð½Ñ Ð¿Ð°Ñ€Ð°Ð»ÐµÐ»Ñ–Ð·Ð¼Ñƒ Ð´Ð»Ñ Ð»Ñ–Ñ†ÐµÐ½Ð·Ñ–Ð¹ Ñ‚Ð° Ð°ÐºÑ‚Ð¸Ð²Ð°Ñ†Ñ–Ð¹.
- Ð Ñ–Ð·Ð½Ñ– ÐºÐ¾Ñ€Ð¸ÑÑ‚ÑƒÐ²Ð°Ñ‡Ñ– Ð¼Ð¾Ð¶ÑƒÑ‚ÑŒ Ð¼Ð°Ñ‚Ð¸ Ñ€Ñ–Ð·Ð½Ñ– Ð¿Ñ€Ð°Ð²Ð° Ñ‚Ð° ÐºÐ²Ð¾Ñ‚Ð¸ Ð·Ð°Ð»ÐµÐ¶Ð½Ð¾ Ð²Ñ–Ð´ Ñ€Ð¾Ð»Ñ–, source binding Ñ– product access policy. Ð”ÐµÑ‚Ð°Ð»ÑŒÐ½Ñ–ÑˆÐµ: [docs/core-security-model.md](docs/core-security-model.md).

### API
ÐŸÐ¾Ñ‚Ð¾Ñ‡Ð½Ð¸Ð¹ API Ð½Ð°Ð´Ð°Ñ” Ð°Ð²Ñ‚ÐµÐ½Ñ‚Ð¸Ñ„Ñ–ÐºÐ¾Ð²Ð°Ð½Ð¸Ð¹ read-access Ð´Ð¾ orders Ñ– licenses Ñ‡ÐµÑ€ÐµÐ· `src/main/java/com/zillya/timonfech/zillwrapper/api`.  
Ð Ð¾Ð»Ñ– `ADMIN`, `MANAGER` Ñ– `LLM_READONLY` Ð¼Ð°ÑŽÑ‚ÑŒ Ð´Ð¾ÑÑ‚ÑƒÐ¿ Ð´Ð¾ read endpoints. ÐžÐ´Ð¸Ð½ Ð·Ð°Ð¿Ð¸Ñ‚ Ð¿Ð¾Ð²ÐµÑ€Ñ‚Ð°Ñ” Ð²Ñ–Ð´ `1` Ð´Ð¾ `500` Ð·Ð°Ð¿Ð¸ÑÑ–Ð². Ð”Ð»Ñ `LLM_READONLY` masking sessions ÑƒÐ²Ñ–Ð¼ÐºÐ½ÐµÐ½Ñ– Ð·Ð° Ð·Ð°Ð¼Ð¾Ð²Ñ‡ÑƒÐ²Ð°Ð½Ð½ÑÐ¼, Ð° Ð´Ð»Ñ human API users Ð²Ð¾Ð½Ð¸ Ð´Ð¾ÑÑ‚ÑƒÐ¿Ð½Ñ– Ð»Ð¸ÑˆÐµ Ñ‡ÐµÑ€ÐµÐ· Ð¾ÐºÑ€ÐµÐ¼Ñƒ ÐºÐ¾Ð½Ñ„Ñ–Ð³ÑƒÑ€Ð°Ñ†Ñ–ÑŽ. Endpoints Ð´Ð»Ñ Ð³ÐµÐ½ÐµÑ€Ð°Ñ†Ñ–Ñ— Ñ‚Ð° mutation-Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹ Ñ‰Ðµ Ñ€Ð¾Ð·Ñ€Ð¾Ð±Ð»ÑÑŽÑ‚ÑŒÑÑ.

### ÐžÑÐ½Ð¾Ð²Ð½Ð° Ð°Ñ€Ñ…Ñ–Ñ‚ÐµÐºÑ‚ÑƒÑ€Ð°
- ÐšÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚ Ñ€Ð¾ÑƒÑ‚Ð¸Ð½Ð³Ñƒ Ð¹ Ð½Ð°Ð¼Ñ–Ñ€Ñ–Ð²: `RoutingService`, `IntentRouter`, `RoutingDecision` Ñƒ [docs/core-routing-and-intent.md](docs/core-routing-and-intent.md)
- ÐšÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚ Ð¿Ð°Ñ€ÑÐ¸Ð½Ð³Ñƒ Ñ‚Ð° Ð´Ñ–Ð°Ð³Ð½Ð¾ÑÑ‚Ð¸ÐºÐ¸: `IMatcher<T>`, `MatchingException`, `OrderTextParser` Ñƒ [docs/core-regex-and-parse-diagnostics.md](docs/core-regex-and-parse-diagnostics.md)
- ÐžÐ¿ÐµÑ€Ð°Ñ†Ñ–Ð¹Ð½Ð¸Ð¹ ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚ Ñ– Ð²Ð¸ÐºÐ¾Ð½Ð°Ð½Ð½Ñ pipeline: `OrderOperationContext`, `PipelineDispatcher`, `OperationGraphRegistry`, `OperationLifecycleAspect` Ñƒ [docs/core-pipeline.md](docs/core-pipeline.md)
- ÐšÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚ questions, answers Ñ– resume: `Question`, `Answer`, `ResumableOperationHandler`, `TelegramInteractionAnswerOrchestrator` Ñƒ [docs/core-interactions-and-resume.md](docs/core-interactions-and-resume.md)
- ÐŸÑ€Ð¸Ð¹Ð¾Ð¼ Ð´Ð¶ÐµÑ€ÐµÐ» Ñ– Ð°Ð²Ñ‚ÐµÐ½Ñ‚Ð¸Ñ„Ñ–ÐºÐ°Ñ†Ñ–Ñ: `InboundEventOrchestrator`, `InboundEvent`, `AuthenticationHandler`, `IdentityExtractor` Ñƒ [docs/core-source-and-auth.md](docs/core-source-and-auth.md)
- Runtime Ñ– ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ†Ñ–Ñ pending-Ð·Ð°Ð´Ð°Ñ‡: `OperationRuntimeRegistry`, `PendingTaskService`, `PendingTaskExecutor` Ñƒ [docs/core-runtime-and-pending.md](docs/core-runtime-and-pending.md)
- ÐŸÐ¾ÑˆÑƒÐº Ñ– Ð¾Ð¿ÐµÑ€Ð°Ñ‚Ð¾Ñ€ÑÑŒÐºÑ– Ð´Ñ–Ñ—: `SearchQueryParser`, `UnifiedSearchService`, `CommandInteractionService`, `LicenseActionExecutor` Ñƒ [docs/core-search-and-actions.md](docs/core-search-and-actions.md)
- ÐšÐ¾Ð¼ÑƒÐ½Ñ–ÐºÐ°Ñ†Ñ–Ñ Ñ‚Ð° UX-Ð¿Ñ€Ð¾Ñ”ÐºÑ†Ñ–Ñ: `TelegramControlMessageService`, `EmailNotifyService`, `EmailCommunicationService` Ñƒ [docs/core-communication-and-ux.md](docs/core-communication-and-ux.md)
- Enrichment engine: `EnrichmentTaskManager`, `EnrichmentOrchestrator`, `EnrichmentActivationRuntimeService` Ñƒ [docs/apis-enrichment.md](docs/apis-enrichment.md)

### ÐÐ°Ð´Ñ–Ð¹Ð½Ñ–ÑÑ‚ÑŒ Ñ– Ð²Ñ–Ð´Ð½Ð¾Ð²Ð»ÑŽÐ²Ð°Ð½Ñ–ÑÑ‚ÑŒ
Ð¡Ð¸ÑÑ‚ÐµÐ¼Ð° Ð·Ð±ÐµÑ€Ñ–Ð³Ð°Ñ” Ð¶Ð¸Ñ‚Ñ‚Ñ”Ð²Ð¸Ð¹ Ñ†Ð¸ÐºÐ» Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹ Ñ– Ð·Ð°Ð¿Ð¸ÑÐ¸ Ð²Ð¸ÐºÐ¾Ð½Ð°Ð½Ð½Ñ ÑÑ‚Ð°Ð´Ñ–Ð¹, Ð° Ð¿Ð¾Ñ‚Ñ–Ð¼ ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½ÑƒÑ” Ð¿Ñ€Ð¾Ð³Ñ€ÐµÑ Ñ‡ÐµÑ€ÐµÐ· Ð´ÐµÑ‚ÐµÑ€Ð¼Ñ–Ð½Ð¾Ð²Ð°Ð½Ñ– handler-Ð¸ Ñ‚Ð° async completion bridges.  
Ð¦Ðµ Ð·Ð°Ð±ÐµÐ·Ð¿ÐµÑ‡ÑƒÑ” Ð²Ñ–Ð´Ð½Ð¾Ð²Ð»ÑŽÐ²Ð°Ð½Ðµ Ð²Ð¸ÐºÐ¾Ð½Ð°Ð½Ð½Ñ Ñ– ÐºÐ¾Ð½Ñ‚Ñ€Ð¾Ð»ÑŒÐ¾Ð²Ð°Ð½Ðµ Ð²Ñ–Ð´Ð½Ð¾Ð²Ð»ÐµÐ½Ð½Ñ Ð¿Ñ–ÑÐ»Ñ Ð¿ÐµÑ€ÐµÑ€Ð¸Ð²Ð°Ð½ÑŒ.

### Ð Ð¾Ð·ÑˆÐ¸Ñ€ÑŽÐ²Ð°Ð½Ñ–ÑÑ‚ÑŒ
- Ð Ð¾Ð·ÑˆÐ¸Ñ€ÐµÐ½Ð½Ñ Ð½Ð° Ñ€Ñ–Ð²Ð½Ñ– Ð´Ð¶ÐµÑ€ÐµÐ» Ð±Ð°Ð·ÑƒÑ”Ñ‚ÑŒÑÑ Ð½Ð° ÐºÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚Ð°Ñ… `InboundEvent`, `IntentRouter` Ñ– source orchestrator.
- Ð Ð¾Ð·ÑˆÐ¸Ñ€ÐµÐ½Ð½Ñ Ð½Ð° Ñ€Ñ–Ð²Ð½Ñ– Ñ‚Ñ€Ð°Ð½ÑÐ¿Ð¾Ñ€Ñ‚Ñƒ Ð±Ð°Ð·ÑƒÑ”Ñ‚ÑŒÑÑ Ð½Ð° routing Ñ‚Ð° interaction-Ð°Ð±ÑÑ‚Ñ€Ð°ÐºÑ†Ñ–ÑÑ…, Ñ‚Ð¾Ð¼Ñƒ Telegram-UX Ð¼Ð¾Ð¶Ð½Ð° Ð·Ð°Ð¼Ñ–Ð½Ð¸Ñ‚Ð¸ Ñ–Ð½ÑˆÐ¸Ð¼ Ð°Ð´Ð°Ð¿Ñ‚ÐµÑ€Ð¾Ð¼ ÐºÐ°Ð½Ð°Ð»Ñƒ Ð±ÐµÐ· Ð·Ð¼Ñ–Ð½Ð¸ ÑÐµÐ¼Ð°Ð½Ñ‚Ð¸ÐºÐ¸ Ð¾Ð¿ÐµÑ€Ð°Ñ†Ñ–Ð¹.
- Ð“ÐµÐ½ÐµÑ€Ð°Ñ†Ñ–Ñ Ð°Ñ€Ñ‚ÐµÑ„Ð°ÐºÑ‚Ñ–Ð² Ñ– notify Ð²Ð¸Ð½ÐµÑÐµÐ½Ñ– Ð² Ð¾ÐºÑ€ÐµÐ¼Ñ– pipeline stages, Ð·Ð¾ÐºÑ€ÐµÐ¼Ð° `LicenseArtifactGenerationHandler` Ñ– `NotifyHandler`.

### Ð‘ÐµÐ·Ð¿ÐµÐºÐ°
ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð±ÐµÐ·Ð¿ÐµÐºÐ¸ Ñ‚Ð° Ð²Ð¸Ð¼Ð¾Ð³Ð¸ Ð´Ð¾ runtime-ÐºÐ¾Ð½Ñ„Ñ–Ð³ÑƒÑ€Ð°Ñ†Ñ–Ñ— Ð¾Ð¿Ð¸ÑÐ°Ð½Ñ– Ð² [docs/core-security-model.md](docs/core-security-model.md).

### Ð†Ð½Ð´ÐµÐºÑ Ð´Ð¾ÐºÑƒÐ¼ÐµÐ½Ñ‚Ð°Ñ†Ñ–Ñ—
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
