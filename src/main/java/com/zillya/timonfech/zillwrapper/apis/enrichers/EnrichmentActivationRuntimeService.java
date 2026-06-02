package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class EnrichmentActivationRuntimeService {

    private final ConcurrentHashMap<UUID, TaskRuntimeState> states = new ConcurrentHashMap<>();
    private final LicenseRepository licenseRepository;
    private final EnrichmentProgressRegistry progressRegistry;
    private final java.util.List<ActivationProvider> activationProviders;
    private final TransactionTemplate transactionTemplate;

    public EnrichmentActivationRuntimeService(LicenseRepository licenseRepository,
                                              EnrichmentProgressRegistry progressRegistry,
                                              java.util.List<ActivationProvider> activationProviders,
                                              PlatformTransactionManager txManager) {
        this.licenseRepository = licenseRepository;
        this.progressRegistry = progressRegistry;
        this.activationProviders = activationProviders;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    public void initTask(UUID taskId) {
        if (taskId == null) {
            return;
        }
        states.putIfAbsent(taskId, new TaskRuntimeState());
    }

    public void removeTask(UUID taskId) {
        if (taskId == null) {
            return;
        }
        states.remove(taskId);
    }

    public void markProducerDone(UUID taskId) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null) {
            return;
        }
        state.producerDone.set(true);
    }

    public void enqueueIfRequired(UUID taskId,
                                  Long licenseId,
                                  Long externalId,
                                  Long sourceId,
                                  Integer productId,
                                  ActivationProviderType providerType,
                                  boolean activationsRequired) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null || licenseId == null || externalId == null || providerType == null) {
            return;
        }
        if (!activationsRequired) {
            progressRegistry.markActivationsSkipped(taskId);
            return;
        }
        if (!state.enqueuedLicenseIds.add(licenseId)) {
            return;
        }
        state.queue.offer(new ActivationCandidate(licenseId, externalId, sourceId, productId, providerType, 1));
        progressRegistry.markActivationsRequired(taskId);
    }

    public void startConsumers(UUID taskId,
                               AtomicBoolean cancelToken,
                               ExecutorService executor,
                               int workerCount,
                               int maxAttempts,
                               long retryBackoffMs) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null || executor == null) {
            return;
        }
        int safeWorkers = Math.max(1, workerCount);
        int safeAttempts = Math.max(1, maxAttempts);
        long safeBackoff = Math.max(0L, retryBackoffMs);
        for (int i = 0; i < safeWorkers; i++) {
            executor.execute(() -> consumeLoop(taskId, state, cancelToken, safeAttempts, safeBackoff));
        }
    }

    public boolean awaitDrain(UUID taskId, long timeoutMs) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() <= deadline) {
            if (isDrained(state)) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return isDrained(state);
            }
        }
        return isDrained(state);
    }

    private void consumeLoop(UUID taskId,
                             TaskRuntimeState state,
                             AtomicBoolean cancelToken,
                             int maxAttempts,
                             long retryBackoffMs) {
        while (true) {
            if (cancelToken != null && cancelToken.get()) {
                return;
            }
            if (isDrained(state)) {
                return;
            }
            ActivationCandidate candidate;
            try {
                candidate = state.queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (candidate == null) {
                continue;
            }

            state.inFlightActivations.incrementAndGet();
            try {
                boolean done = processCandidate(taskId, candidate);
                if (!done) {
                    if (candidate.attempt() < maxAttempts) {
                        if (retryBackoffMs > 0) {
                            try {
                                Thread.sleep(retryBackoffMs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        state.queue.offer(new ActivationCandidate(
                                candidate.licenseId(),
                                candidate.externalId(),
                                candidate.sourceId(),
                                candidate.productId(),
                                candidate.providerType(),
                                candidate.attempt() + 1
                        ));
                    } else {
                        progressRegistry.markActivationsFailed(taskId);
                    }
                }
            } finally {
                state.inFlightActivations.decrementAndGet();
            }
        }
    }

    private boolean processCandidate(UUID taskId, ActivationCandidate candidate) {
        try {
            Boolean done = transactionTemplate.execute(status -> {
                Long keyId = licenseRepository.findKeyIdByLicenseId(candidate.licenseId()).orElse(null);
                if (keyId == null) {
                    progressRegistry.markActivationsSkipped(taskId);
                    return true;
                }
                Integer productId = candidate.productId() != null
                        ? candidate.productId()
                        : licenseRepository.findProductIdByLicenseId(candidate.licenseId()).orElse(null);
                ActivationProvider provider = activationProviders.stream()
                        .filter(p -> p.supports(candidate.providerType()))
                        .findFirst()
                        .orElse(null);
                if (provider == null) {
                    progressRegistry.markActivationsSkipped(taskId);
                    return true;
                }
                try {
                    provider.enrich(keyId, candidate.externalId(), productId);
                    progressRegistry.markActivationsProcessed(taskId);
                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
            return done == null || done;
        } catch (Exception ex) {
            log.warn("activation_enrichment_failed taskId={} licenseId={} externalId={} attempt={} reason={}",
                    taskId, candidate.licenseId(), candidate.externalId(), candidate.attempt(), ex.getMessage());
            return false;
        }
    }

    private boolean isDrained(TaskRuntimeState state) {
        return state.producerDone.get()
                && state.queue.isEmpty()
                && state.inFlightActivations.get() == 0;
    }

    private record ActivationCandidate(Long licenseId,
                                       Long externalId,
                                       Long sourceId,
                                       Integer productId,
                                       ActivationProviderType providerType,
                                       int attempt) {}

    private static final class TaskRuntimeState {
        private final BlockingQueue<ActivationCandidate> queue = new LinkedBlockingQueue<>();
        private final Set<Long> enqueuedLicenseIds = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean producerDone = new AtomicBoolean(false);
        private final AtomicInteger inFlightActivations = new AtomicInteger(0);
    }
}
