# Core Regex and Parse Diagnostics

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/regex`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/regex/order`

## Primary classes/interfaces
- `IMatcher<T>`
- `MatchingException`
- `OrderTextParser`
- `OrderReferenceLineParser`
- `OrderItemLineParser`
- `EmailLineParser`
- `FlagParser`
- `ParameterFlagParser`
- `OrderParseException`

## Runtime flow summary
- `RegexOrderTelegramRouter` passes incoming text to `OrderTextParser`.
- `OrderTextParser` splits input into reference line, item lines, email lines, and global flags.
- Line-level parsers (`OrderReferenceLineParser`, `OrderItemLineParser`, `EmailLineParser`) use `IMatcher<T>` contracts to map regex matches into typed values.
- Parse failures are converted into `OrderParseException` and returned as user-facing parsing errors.

## `IMatcher<T>` contract and mismatch behavior
- `match(String)` returns `Optional<T>` and keeps a structured failure cause in `getCause()`.
- `matchOrThrow(String)` throws the exact `MatchingException` produced by matcher logic.
- `IMatcher.regex(...)` compiles patterns with `CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS`.
- `IMatcher.firstOf(...)` tries a list of matchers and emits one consolidated failure when none matches.
- `MatchingException` carries:
  - `index` (where parsing failed),
  - `expectedRegex`,
  - `text`,
  - `matchedPart`.

## What users see when parsing fails
- Router-level failures are surfaced as `Order parsing error: ...`.
- Typical failure classes:
  - unknown/invalid flags (`FlagParser`, `ParameterFlagParser`),
  - malformed item syntax (`OrderItemLineParser`),
  - invalid reference/email lines.
- The parser path is strict by contract: invalid syntax does not silently mutate input into another intent.

## Flag abstractions and negative forms
- `FlagParser` handles boolean-style flags that toggle behavior in parsed order input.
- `ParameterFlagParser` handles flags that require a value, for example locale or subscription timing parameters.
- The grammar is centralized so the same flag semantics are reused by order parsing and help text instead of being duplicated in routers.
- Some flags explicitly support negative forms, for example enabling/disabling subscription or output behavior. This keeps the command language symmetric and reduces hidden defaults in the parser path.

## Extension points
- Add a new grammar fragment by creating a dedicated matcher/parsing class and wiring it in `OrderTextParser`.
- Keep diagnostics structured through `MatchingException` rather than plain string-only errors.
- Keep flag grammar centralized in `FlagParser` and `ParameterFlagParser` to avoid parser drift across routers.

## Related docs
- [core-routing-and-intent.md](core-routing-and-intent.md)
- [core-pipeline.md](core-pipeline.md)
