# Core Pipeline

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/pipeline`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/pipeline/plan`

## Primary classes/interfaces
- `PipelineDispatcher`
- `OperationGraphRegistry`
- `OperationPlanBuilder`
- `CoreOperationPlanBuilder`
- `OperationLifecycleAspect`
- `OperationExecutionEntity`
- `OrderCreationHandler`
- `OrderUpdateStartHandler`
- `LicenseGenerationHandler`
- `LicenseSubscriptionSetupHandler`
- `LicenseArtifactGenerationHandler`
- `NotifyHandler`
- `ResendNotificationHandler`

## Runtime flow summary
`PipelineDispatcher` executes staged operations using `OperationHandler` implementations selected by `OperationType`.  
`OperationGraphRegistry` and `OperationPlanBuilder` provide execution-order plans for operation roots.  
The pipeline engine is operation-domain agnostic; stage handlers are pluggable and isolated by contract.

Current domain handlers include:
- `OrderCreationHandler`
- `OrderUpdateStartHandler`
- `LicenseGenerationHandler`
- `LicenseSubscriptionSetupHandler`
- `LicenseArtifactGenerationHandler`
- `NotifyHandler`
- `ResendNotificationHandler`

## Lifecycle aspect and operation state model
- `OperationLifecycleAspect` is the lifecycle boundary for `@OperationStep` handlers: it resolves parent/stage execution rows, applies pause/cancel/wait rules, persists stage transitions, and emits interaction events.
- `OperationExecutionEntity` is the persisted operation state contract in `operation_execution`: parent-child linkage, stage/root type, status, recoverability, interaction payload (`questionJson`), execution plan snapshot (`executionPlanJson`), ordering (`sequenceNo`), and optimistic lock (`stateVersion`).
- Question-based flows are persisted through `questionType/questionJson` and raised via `QuestionRequiredEvent`; execution resume then continues from persisted stage state.
- Finalization is not decided by UI components: terminal transitions are persisted by pipeline/aspect paths and projected later by communication services.

## Extension points
- Add a new stage by implementing `OperationHandler<IOperationContext>` and binding a new `OperationType`.
- Extend plan composition by adding/updating an `OperationPlanBuilder` implementation.
- Add source-specific start behavior through source orchestrators while reusing core pipeline contracts.

## Related docs
- [../OPERATION_ARCHITECTURE_NOTES.md](../OPERATION_ARCHITECTURE_NOTES.md)
- [core-runtime-and-pending.md](core-runtime-and-pending.md)
- [core-communication-and-ux.md](core-communication-and-ux.md)
- [core-regex-and-parse-diagnostics.md](core-regex-and-parse-diagnostics.md)
