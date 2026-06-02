package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBindingUpdateService {

    private final TelegramOperationBindingRepository bindingRepository;
    private final Map<BigInteger, ReentrantLock> operationLocks = new ConcurrentHashMap<>();

    public Optional<TelegramOperationBindingEntity> applyByOperationId(BigInteger operationId,
                                                                       String reason,
                                                                       Function<TelegramOperationBindingEntity, Boolean> patchFn) {
        if (operationId == null || patchFn == null) {
            return Optional.empty();
        }
        ReentrantLock lock = operationLocks.computeIfAbsent(operationId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return applyWithRetry(() -> bindingRepository.findByOperationId(operationId), operationId, reason, patchFn);
        } finally {
            lock.unlock();
        }
    }

    public Optional<TelegramOperationBindingEntity> applyByBindingId(Long bindingId,
                                                                     String reason,
                                                                     Function<TelegramOperationBindingEntity, Boolean> patchFn) {
        if (bindingId == null || patchFn == null) {
            return Optional.empty();
        }
        TelegramOperationBindingEntity existing = bindingRepository.findById(bindingId).orElse(null);
        if (existing == null || existing.getOperationId() == null) {
            return Optional.empty();
        }
        return applyByOperationId(existing.getOperationId(), reason, patchFn);
    }

    private Optional<TelegramOperationBindingEntity> applyWithRetry(Loader loader,
                                                                    BigInteger operationId,
                                                                    String reason,
                                                                    Function<TelegramOperationBindingEntity, Boolean> patchFn) {
        int[] backoff = new int[] {20, 40, 80, 120, 180};
        for (int attempt = 1; attempt <= backoff.length; attempt++) {
            Instant started = Instant.now();
            Optional<TelegramOperationBindingEntity> bindingOpt = loader.load();
            if (bindingOpt.isEmpty()) {
                return Optional.empty();
            }
            TelegramOperationBindingEntity binding = bindingOpt.get();
            boolean changed = Boolean.TRUE.equals(patchFn.apply(binding));
            if (!changed) {
                log.debug("binding_update_noop reason={} operationId={} bindingId={} attempt={}",
                        reason, operationId, binding.getId(), attempt);
                return Optional.of(binding);
            }
            try {
                TelegramOperationBindingEntity saved = bindingRepository.save(binding);
                long durationMs = Duration.between(started, Instant.now()).toMillis();
                log.debug("binding_update_success reason={} operationId={} bindingId={} attempt={} durationMs={}",
                        reason, operationId, saved.getId(), attempt, durationMs);
                return Optional.of(saved);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.warn("binding_update_conflict reason={} operationId={} bindingId={} attempt={}/{}",
                        reason, operationId, binding.getId(), attempt, backoff.length);
                if (attempt == backoff.length) {
                    return Optional.empty();
                }
                sleep(backoff[attempt - 1]);
            }
        }
        return Optional.empty();
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Loader {
        Optional<TelegramOperationBindingEntity> load();
    }
}

