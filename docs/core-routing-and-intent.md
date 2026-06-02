# Core Routing and Intent

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/routing`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/regex/order`

## Primary classes/interfaces
- `RoutingService`
- `IntentRouter<C extends InboundEvent<?>>`
- `RoutingDecision`
- `RegexOrderTelegramRouter`
- `TelegramControlCallbackRouter`
- `TelegramInteractionRouter`
- `TelegramMenuRouter`
- `OrderTextParser`
- `OrderReferenceLineParser`
- `OrderItemLineParser`
- `IMatcher<T>`
- `MatchingException`

## Runtime flow summary
`RoutingService` iterates `IntentRouter` implementations and selects the first accepted route.  
`IntentRouter` and `RoutingDecision` are generic routing contracts for source-specific intents.  
`RegexOrderTelegramRouter` is the current Telegram adapter that parses structured order messages and command payloads into `OrderOperationContext`.  
Routing produces typed `RoutingDecision` variants that are executed by pipeline-level coordinators.

## Parsing diagnostics contract
- Text parsing for structured order input is matcher-driven (`IMatcher<T>`), with deterministic failure causes (`MatchingException`).
- Router-visible parser failures are wrapped into `OrderParseException` and surfaced as user-readable validation errors instead of silent fallback routing.
- Flag grammar is centralized (`FlagParser`, `ParameterFlagParser`) to keep behavior consistent across `/s`, actions, and plain order parsing paths.

## Extension points
- Add a new intent path by implementing `IntentRouter` and registering it with ordering.
- Add/adjust command semantics through router parsing rules and payload decorators.
- Add parsing rules by extending regex parsers without changing downstream pipeline contracts.

## Related docs
- [core-pipeline.md](core-pipeline.md)
- [core-source-and-auth.md](core-source-and-auth.md)
- [core-regex-and-parse-diagnostics.md](core-regex-and-parse-diagnostics.md)
- [core-search-and-actions.md](core-search-and-actions.md)
