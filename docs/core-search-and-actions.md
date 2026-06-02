# Core Search and Actions

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/search`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/interactions/commands`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/pipeline/actions`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/transport`

## Primary classes/interfaces
- `SearchQuery`
- `SearchQueryParser`
- `SearchEntityType`
- `SearchResolver<T>`
- `UnifiedSearchService`
- `OrderSearchResolver`
- `LicenseSearchResolver`
- `EntityViewRenderer<T>`
- `OrderViewRenderer`
- `LicenseViewRenderer`
- `SearchSession`
- `SearchSessionStore`
- `CommandInteractionService`
- `TelegramSearchActionOrchestrator`
- `LicenseActionExecutor`
- `DefaultLicenseActionExecutor`
- `WhiteAdminLicenseActionExecutor`

## Runtime flow summary
- Search commands are parsed into `CommandIntent`, then opened through `CommandInteractionService`.
- `SearchQueryParser` converts raw payload into a typed `SearchQuery`, supporting both `key=value` and space-separated selector forms.
- `UnifiedSearchService` selects the correct `SearchResolver` and `EntityViewRenderer` for `ORDER` or `LICENSE` results.
- `SearchSessionStore` keeps the interactive search session used by Telegram paging and confirm/cancel flows.

## Selectors, filters, and correlation
- Search selectors currently include `wzid`, `wid2`, `pid`, `woid`, `lex`, `kid`, `kon`, `kof`, `comment`, `orderId`, and `entity`.
- Product filtering is supported through `-p` / `product`.
- `ReplyCorrelationResolver` and `UnifiedSearchService.resolveOrderIdByReplyCorrelation(...)` allow action flows to inherit target context from a replied order/control message.
- Key selectors are guarded by minimum-length validation in `CommandInteractionService` to avoid broad accidental mutations.

## Action flows built on search
- `/block`, `/allow`, `/detach`, and `/resend` do not implement a separate target-selection system.
- They reuse the same search/session model, then switch the session into action mode.
- `TelegramSearchActionOrchestrator` presents:
  - result paging,
  - confirm current,
  - confirm all,
  - cancel.
- Bulk actions have guardrails such as candidate caps and extra confirmation thresholds before mass mutation.

## Execution after confirmation
- After confirmation, `CommandInteractionService` creates an `OrderOperationContext` for the action operation.
- `OperationGraphRegistry` builds the execution plan, `OperationExecutionService` creates the parent operation and planned stages, and `PipelineDispatcher` runs the action pipeline.
- For license mutations, provider-specific behavior is delegated to `LicenseActionExecutor` implementations such as `WhiteAdminLicenseActionExecutor`.
- For resend, target resolution is scoped through resend target metadata rather than a separate command-only pipeline.

## Extension points
- Add new selectors in `SearchQueryParser` and the matching resolver path.
- Add new entity views by implementing `EntityViewRenderer<T>`.
- Add new mutation backends by implementing `LicenseActionExecutor`.
- Keep action UX on top of the shared search/session contract instead of duplicating selection logic per command.

## Related docs
- [core-routing-and-intent.md](core-routing-and-intent.md)
- [core-runtime-and-pending.md](core-runtime-and-pending.md)
- [core-security-model.md](core-security-model.md)

