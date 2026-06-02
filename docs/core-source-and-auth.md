# Core Source and Authentication

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/source`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/security`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/pipeline` (entry orchestration)

## Primary classes/interfaces
- `InboundEvent<T>`
- `TelegramInboundEvent`
- `InboundEventOrchestrator`
- `AuthenticationHandler<T>`
- `TelegramAuthenticationHandler`
- `IdentityExtractor<T>`
- `TelegramIdentityExtractor`
- `IdentityService`
- `UserAuthenticationService`

## Runtime flow summary
Transport adapters produce typed `InboundEvent` instances (for example `TelegramInboundEvent`).  
`InboundEventOrchestrator` applies source-agnostic authentication and access checks before routing and pipeline start.  
`UserAuthenticationService` validates identity factors against `UserSourceEntity` and source-bound security factors.

Current transport/source implementation is Telegram-based (`TelegramInboundEvent`, `TelegramAuthenticationHandler`, `TelegramIdentityExtractor`), but the contract is generic.

## Extension points
- Add a new source type by introducing a new `InboundEvent` + `AuthenticationHandler` + `IdentityExtractor`.
- Keep identity-factor policy in `UserSourceEntity.SecurityFactor` and `UserAuthenticationService`.
- Preserve router and pipeline contracts; source adapters should remain isolated from stage/business orchestration logic.

## Related docs
- [core-routing-and-intent.md](core-routing-and-intent.md)
- [core-security-model.md](core-security-model.md)
