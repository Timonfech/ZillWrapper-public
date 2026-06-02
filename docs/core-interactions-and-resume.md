# Core Interactions and Resume

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/interactions`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/transport`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/interfaces`

## Primary classes/interfaces
- `Question<A extends Answer>`
- `Answer`
- `YesNoQuestion`
- `DuplicateQuestion`
- `NewStringsQuestion`
- `YesNoAnswer`
- `StringsListAnswer`
- `NeedUserInteractionException`
- `ResumableOperationHandler<Q, A>`
- `TelegramInteractionAnswerOrchestrator`
- `TelegramResolvedQuestion`
- `TelegramQuestionQueueItem`
- `QuestionRequiredEvent`

## Runtime flow summary
- A stage handler raises `NeedUserInteractionException` when it cannot complete without user input.
- `OperationLifecycleAspect` persists the waiting state in `OperationExecutionEntity` through `questionType` and `questionJson`, then emits `QuestionRequiredEvent`.
- `TelegramControlMessageService` renders and queues the question, while `TelegramInteractionAnswerOrchestrator` accepts reply text or callback answers for the waiting stage.
- The resume path is type-aware: `ResumableOperationHandler<Q, A>` decides whether a concrete handler supports a given `Question`/`Answer` pair and then resumes execution.

## Question and answer abstractions
- `Question<A extends Answer>` is the typed prompt contract used by resumable stages.
- `Answer` is the typed reply contract; current built-in answers are `YesNoAnswer` and `StringsListAnswer`.
- Current built-in question types are:
  - `YesNoQuestion` for explicit binary confirmation,
  - `DuplicateQuestion` for duplicate detection and resend/append style decisions,
  - `NewStringsQuestion` for corrected free-text input such as email replacement.
- Both `Question` and `Answer` are polymorphic JSON contracts, so persisted interaction payloads can be deserialized and resumed later.

## Resume contract
- `ResumableOperationHandler<Q, A>` keeps resume logic next to the stage handler instead of pushing answer resolution into transport code.
- `supports(stageExecution, question, answer)` is the type/shape guard.
- `resume(stageExecution, question, answer)` returns `OperationResult<?>` and may raise another `NeedUserInteractionException` if the follow-up input is still incomplete.
- `OrderCreationHandler` is the main current example: it resumes both duplicate-confirmation and corrected-email flows.

## Transport and ownership rules
- Text answers are accepted only when they reply to the specific question message.
- Callback answers are accepted only from the specific waiting question/control context.
- `TelegramInteractionAnswerOrchestrator` resolves the waiting question, validates ownership/readiness, deserializes the stored `Question`, parses an `Answer`, and calls the resumable handler path.
- `TelegramQuestionQueueItem` and `TelegramResolvedQuestion` keep question identity, delivery state, and answer payload tracking in the Telegram binding layer.

## Extension points
- Add a new interaction type by introducing a new `Question` subtype, matching `Answer` subtype if needed, and resume support in a `ResumableOperationHandler`.
- Keep transport adapters generic: question semantics belong in handlers, not in Telegram-only branching.
- Preserve persisted `questionType/questionJson` compatibility when extending question models.

## Related docs
- [core-runtime-and-pending.md](core-runtime-and-pending.md)
- [core-pipeline.md](core-pipeline.md)
- [core-communication-and-ux.md](core-communication-and-ux.md)
