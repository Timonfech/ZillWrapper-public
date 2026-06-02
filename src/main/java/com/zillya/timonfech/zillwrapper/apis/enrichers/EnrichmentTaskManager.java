package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentTaskManager {

    private final ConcurrentHashMap<UUID, AtomicBoolean> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BigInteger, UUID> operationTaskIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TrackedRun> trackedRuns = new ConcurrentHashMap<>();
    private final AtomicInteger runningTasks = new AtomicInteger(0);
    private final EnrichmentOrchestrator orchestrator;
    private final OperationExecutionService operationExecutionService;
    private final EnrichmentProgressRegistry progressRegistry;
    private final EnrichmentParallelismSettingsService parallelismSettingsService;
    private final EnrichmentActivationRuntimeService activationRuntimeService;
    @Qualifier("enrichmentTaskExecutor")
    private final ExecutorService enrichmentTaskExecutor;
    @Value("${enrichment.max-concurrent-runs:8}")
    private int maxConcurrentRuns;
    @Value("${enrichment.activations.max-attempts:3}")
    private int activationsMaxAttempts;
    @Value("${enrichment.activations.retry-backoff-ms:250}")
    private long activationsRetryBackoffMs;
    @Value("${enrichment.activations.drain-timeout-ms:30000}")
    private long activationsDrainTimeoutMs;

    public UUID startEnrichment(EnrichmentRequest request) {
        return startInternal(request, null);
    }

    public UUID startEnrichmentTracked(EnrichmentRequest request, BigInteger parentOperationId, BigInteger stageExecutionId, boolean single) {
        TrackedRun tracked = new TrackedRun(parentOperationId, stageExecutionId, single);
        return startInternal(request, tracked);
    }

    private UUID startInternal(EnrichmentRequest request, TrackedRun tracked) {
        if (runningTasks.incrementAndGet() > maxConcurrentRuns) {
            runningTasks.decrementAndGet();
            if (tracked != null) {
                String reason = "Enrichment rejected: concurrent limit reached";
                operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED, reason);
                operationExecutionService.markParentFailed(tracked.parentOperationId(), reason);
            }
            log.info("enrichment run rejected taskId={} active={} limit={}",
                    request.taskId(), runningTasks.get(), maxConcurrentRuns);
            return request.taskId();
        }

        AtomicBoolean isCancelled = new AtomicBoolean(false);
        activeTasks.put(request.taskId(), isCancelled);
        activationRuntimeService.initTask(request.taskId());
        if (tracked != null) {
            trackedRuns.put(request.taskId(), tracked);
            operationTaskIndex.put(tracked.parentOperationId(), request.taskId());
            progressRegistry.start(request.taskId(), tracked.parentOperationId(), request.from(), request.to(), tracked.single());
        }
        Instant startedAt = Instant.now();
        log.info("enrichment run started taskId={} tracked={} active={}",
                request.taskId(), tracked != null, runningTasks.get());

        try {
            enrichmentTaskExecutor.execute(() -> {
                com.zillya.timonfech.zillwrapper.core.OperationStatus terminalStatus = com.zillya.timonfech.zillwrapper.core.OperationStatus.DONE;
                String terminalSummary = null;
                activationRuntimeService.startConsumers(
                        request.taskId(),
                        isCancelled,
                        enrichmentTaskExecutor,
                        parallelismSettingsService.getActivationsParallelism(),
                        activationsMaxAttempts,
                        activationsRetryBackoffMs
                );

                try {
                    var result = orchestrator.handle(request, isCancelled);
                    activationRuntimeService.markProducerDone(request.taskId());
                    activationRuntimeService.awaitDrain(request.taskId(), activationsDrainTimeoutMs);
                    if (tracked != null) {
                        if (result != null && !result.isSuccess()) {
                            String reason = result.getErrorMessage() == null || result.getErrorMessage().isBlank()
                                    ? "Enrichment failed"
                                    : result.getErrorMessage();
                            if (isCancelled.get()
                                    || operationExecutionService.getStatus(tracked.parentOperationId()) == com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED
                                    || reason.toLowerCase().contains("cancel")) {
                                terminalStatus = com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED;
                                terminalSummary = "Enrichment cancelled";
                                operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED, "Enrichment cancelled");
                            } else {
                                terminalStatus = com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED;
                                terminalSummary = reason;
                                operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED, reason);
                                operationExecutionService.markParentFailed(tracked.parentOperationId(), reason);
                            }
                        } else {
                            String summary = buildSummary(request.taskId());
                            operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.DONE, summary);
                            operationExecutionService.markParentDone(tracked.parentOperationId(), summary);
                            terminalSummary = summary;
                        }
                    }
                } catch (Exception e) {
                    activationRuntimeService.markProducerDone(request.taskId());
                    activationRuntimeService.awaitDrain(request.taskId(), activationsDrainTimeoutMs);
                    terminalStatus = com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED;
                    if (tracked != null) {
                        if (isCancelled.get()
                                || operationExecutionService.getStatus(tracked.parentOperationId()) == com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED) {
                            terminalStatus = com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED;
                            terminalSummary = "Enrichment cancelled";
                            operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.CANCELLED, "Enrichment cancelled");
                        } else {
                            terminalSummary = e.getMessage();
                            operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED, e.getMessage());
                            operationExecutionService.markParentFailed(tracked.parentOperationId(), e.getMessage());
                        }
                    }
                    log.error("Enrichment task {} failed", request.taskId(), e);
                } finally {
                    activeTasks.remove(request.taskId());
                    progressRegistry.finish(request.taskId());
                    activationRuntimeService.removeTask(request.taskId());
                    if (tracked != null) {
                        operationTaskIndex.remove(tracked.parentOperationId());
                        trackedRuns.remove(request.taskId());
                    }
                    runningTasks.decrementAndGet();
                    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                    log.info("enrichment run finished taskId={} status={} durationMs={} active={} summary={}",
                            request.taskId(),
                            terminalStatus,
                            durationMs,
                            runningTasks.get(),
                            terminalSummary == null ? "-" : terminalSummary);
                }
            });
        } catch (RejectedExecutionException ex) {
            activeTasks.remove(request.taskId());
            progressRegistry.finish(request.taskId());
            activationRuntimeService.removeTask(request.taskId());
            if (tracked != null) {
                operationTaskIndex.remove(tracked.parentOperationId());
                trackedRuns.remove(request.taskId());
                String reason = "Enrichment rejected: executor busy";
                operationExecutionService.markStageCompleted(tracked.stageExecutionId(), com.zillya.timonfech.zillwrapper.core.OperationStatus.FAILED, reason);
                operationExecutionService.markParentFailed(tracked.parentOperationId(), reason);
            }
            runningTasks.decrementAndGet();
            log.info("enrichment run rejected taskId={} active={} reason=executor_busy",
                    request.taskId(), runningTasks.get());
        }

        return request.taskId();
    }

    public void cancelTask(UUID taskId) {
        AtomicBoolean token = activeTasks.get(taskId);
        if (token != null) {
            token.set(true);
            log.info("Task {} was marked as cancelled", taskId);
        } else {
            log.warn("Task {} not found for cancellation", taskId);
        }
    }

    public boolean isTaskActive(UUID taskId) {
        return taskId != null && activeTasks.containsKey(taskId);
    }

    public void cancelByOperationId(BigInteger operationId) {
        if (operationId == null) {
            return;
        }
        UUID taskId = operationTaskIndex.get(operationId);
        if (taskId != null) {
            cancelTask(taskId);
        }
    }

    public int getLicenseParallelism() {
        return parallelismSettingsService.getLicenseParallelism();
    }

    public int getActivationsParallelism() {
        return parallelismSettingsService.getActivationsParallelism();
    }

    public void setLicenseParallelism(int value) {
        parallelismSettingsService.setLicenseParallelism(value);
    }

    public void setActivationsParallelism(int value) {
        parallelismSettingsService.setActivationsParallelism(value);
    }

    private String buildSummary(UUID taskId) {
        return progressRegistry.findByTaskId(taskId)
                .map(p -> "Enrichment completed: processed=" + p.processed() + "/" + p.total()
                        + ", current=" + (p.currentExternalId() == null ? "-" : p.currentExternalId()))
                .orElse("Enrichment completed");
    }

    private record TrackedRun(BigInteger parentOperationId, BigInteger stageExecutionId, boolean single) {}
}
