# APIs Enrichment Engine

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/apis/enrichers`
- `src/main/java/com/zillya/timonfech/zillwrapper/apis/enrichers/whiteadmin`

## Primary classes/interfaces
- `EnrichmentTaskManager`
- `EnrichmentOrchestrator`
- `EnrichmentActivationRuntimeService`
- `EnrichmentProgressRegistry`
- `ActivationProvider`
- `WhiteAdminActivationProvider`
- `EntityUpdateParserRegistry`
- `ExternalIdResolver`
- `LicenseDedupService`
- `OrderDedupService`

## Runtime flow summary
`EnrichmentTaskManager` starts tracked runs and integrates with operation execution state.  
License/entity ingestion is coordinated by `EnrichmentOrchestrator` + parser registry per source/entity type.  
Activation enrichment is runtime-queued via `EnrichmentActivationRuntimeService` and provider-specific activation providers with progress reporting through `EnrichmentProgressRegistry`.

## Parallelism and lightweight threads
- Enrichment workers use the shared `enrichmentTaskExecutor` from `TaskExecutorConfig` (`Executors.newVirtualThreadPerTaskExecutor()`), so high I/O fan-out does not require a large platform-thread pool.
- Parallelism is controlled at runtime by `EnrichmentParallelismSettingsService`:
- `enrichment.parallelism.license`
- `enrichment.parallelism.activations`
- License ingestion and activation enrichment are decoupled at runtime: producers enqueue activation candidates, consumer workers process them with retry/backoff and drain barrier completion.

## Extension points
- Add a new provider system by implementing `ActivationProvider` and wiring parser + resolver strategy.
- Add new source parsing by registering implementations in `EntityUpdateParserRegistry`.
- Keep dedup/upsert invariants in `LicenseDedupService` and `OrderDedupService` to avoid identity drift.

## Related docs
- [ENRICHMENT_SIMPLIFICATION_PLAN.md](ENRICHMENT_SIMPLIFICATION_PLAN.md)
- [core-communication-and-ux.md](core-communication-and-ux.md)

