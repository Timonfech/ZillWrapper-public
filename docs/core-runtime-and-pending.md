# Core Runtime and Pending Tasks

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/runtime`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/pending`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/entities/pending`

## Primary classes/interfaces
- `OperationRuntimeRegistry`
- `PendingTaskService`
- `PendingTaskExecutor`
- `PendingTaskCleanupService`
- `PendingTaskPayload`
- `OrderPreviewPendingPayload`
- `OrderPreviewPendingItem`
- `NeedUserInteractionException`
- `QuestionRequiredEvent`
- `HandlingInfoEvent`

## Runtime flow summary
`OperationRuntimeRegistry` stores transient operation context snapshots keyed by parent operation id.  
`PendingTaskService` persists user interaction checkpoints (preview confirm/cancel flows).  
`PendingTaskExecutor` resolves decisions (confirm/cancel/WA placeholder branch), reconciles runtime context, and triggers `PipelineDispatcher`.

## Interaction flow (question/confirm/cancel)
- Stage handlers raise `NeedUserInteractionException` when user confirmation is required.
- `OperationLifecycleAspect` persists waiting state into `OperationExecutionEntity` (`questionType`, `questionJson`) and emits `QuestionRequiredEvent`.
- `PendingTaskService` stores pending task payload; `PendingTaskExecutor` resolves callback decisions and resumes execution with reconciled runtime context.
- `HandlingInfoEvent` drives async control-card updates without coupling message rendering to handler internals.

## Runtime concurrency model
- Long-running and fan-out workloads run through shared executors configured in `TaskExecutorConfig` (virtual-thread per task executor for enrichment workloads).
- Async stage continuation is coordinated by `PipelineDispatcher` + `AsyncStageCoordinator`; stage completion events are applied back to persisted operation state.
- Pending interactions remain deterministic because runtime snapshots (`OperationRuntimeRegistry`) and persisted checkpoints (`PendingTaskService`) are reconciled in a single executor path (`PendingTaskExecutor`).

## Extension points
- Add a new pending interaction type by creating a `PendingTaskPayload` variant and executor branch.
- Keep runtime context reconciliation in one place to avoid divergence between preview payload and execution context.
- Tune cleanup policies in `PendingTaskCleanupService` and pending TTL properties.

## Related docs
- [core-pipeline.md](core-pipeline.md)
- [core-communication-and-ux.md](core-communication-and-ux.md)
- [core-interactions-and-resume.md](core-interactions-and-resume.md)
