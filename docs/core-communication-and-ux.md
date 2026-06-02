# Core Communication and UX Projection

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/communication`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/communication/sections`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/communication/finalization`

## Primary classes/interfaces
- `TelegramControlMessageService`
- `ControlMessageComposer`
- `ControlMessageSectionRenderer`
- `LifecycleHeaderSectionRenderer`
- `StagesTimelineSectionRenderer`
- `EnrichmentProgressSectionRenderer`
- `EmailNotifyService`
- `EmailCommunicationService`
- `EmailTemplateRoutingService`
- `FinalNotificationPolicy`

## Runtime flow summary
UX rendering is projection-oriented: it reflects operation state, question state, and finalization decisions without owning business execution.  
`TelegramControlMessageService` composes control cards from section renderers and lifecycle events.  
Email delivery is split into routing (`EmailTemplateRoutingService`) and transport/render (`EmailCommunicationService`) with per-item logic in `EmailNotifyService`.

## UI decoupling from core execution
- UI adapters do not drive pipeline transitions: business progression is owned by pipeline handlers/orchestrators, while communication services only project current state.
- `ControlMessageComposer` + `ControlMessageSectionRenderer` implementations render lifecycle snapshots and enrichment progress independently from operation execution code.
- Event-driven updates (`HandlingInfoEvent`, lifecycle events, pending-task events) allow control-message refresh without coupling transport logic to stage internals.

## Extension points
- Add new control-card blocks by implementing `ControlMessageSectionRenderer`.
- Add final-result message behavior by implementing `FinalNotificationPolicy`.
- Add new email template routing rules through `EmailRoutingProperties` + routing service without changing handlers.

## Related docs
- [core-runtime-and-pending.md](core-runtime-and-pending.md)
- [core-pipeline.md](core-pipeline.md)
