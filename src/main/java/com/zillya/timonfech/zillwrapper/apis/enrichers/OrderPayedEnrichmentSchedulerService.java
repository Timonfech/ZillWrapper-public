package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.entities.enrichment.EnrichmentSchedulerSettingEntity;
import com.zillya.timonfech.zillwrapper.core.repos.EnrichmentSchedulerSettingRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPayedEnrichmentSchedulerService {
    public static final String JOB_NAME = "ORDER_PAYED";
    private static final int DEFAULT_DELAY_MINUTES = 3;

    private final EnrichmentSchedulerSettingRepository settingRepository;
    private final SourceManagementService sourceManagementService;
    private final EnrichmentTaskManager enrichmentTaskManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastRunAt = Instant.EPOCH;
    private volatile UUID activeTaskId;

    public EnrichmentSchedulerSettingEntity getSettings() {
        return settingRepository.findById(JOB_NAME).orElseGet(this::createDefaultSettings);
    }

    public EnrichmentSchedulerSettingEntity setEnabled(boolean enabled, String updatedBy) {
        EnrichmentSchedulerSettingEntity settings = getSettings();
        boolean wasEnabled = settings.isEnabled();
        settings.setEnabled(enabled);
        settings.setUpdatedBy(updatedBy);
        settings.setUpdatedAt(Instant.now());
        EnrichmentSchedulerSettingEntity saved = settingRepository.save(settings);
        if (enabled && !wasEnabled) {
            runNow();
        }
        return saved;
    }

    public EnrichmentSchedulerSettingEntity setDelayMinutes(int delayMinutes, String updatedBy) {
        if (delayMinutes < 1) {
            throw new IllegalArgumentException("Delay must be >= 1 minute");
        }
        EnrichmentSchedulerSettingEntity settings = getSettings();
        settings.setDelayMinutes(delayMinutes);
        settings.setUpdatedBy(updatedBy);
        settings.setUpdatedAt(Instant.now());
        return settingRepository.save(settings);
    }

    public UUID runNow() {
        if (activeTaskId != null && enrichmentTaskManager.isTaskActive(activeTaskId)) {
            return null;
        }
        if (activeTaskId != null && !enrichmentTaskManager.isTaskActive(activeTaskId)) {
            activeTaskId = null;
            running.set(false);
        }
        return runPayedSnapshot("manual");
    }

    @Scheduled(fixedDelay = 60000)
    public void tickScheduled() {
        if (activeTaskId != null && enrichmentTaskManager.isTaskActive(activeTaskId)) {
            return;
        }
        if (activeTaskId != null && !enrichmentTaskManager.isTaskActive(activeTaskId)) {
            activeTaskId = null;
            running.set(false);
        }
        EnrichmentSchedulerSettingEntity settings = getSettings();
        if (!settings.isEnabled()) {
            return;
        }
        Duration sinceLast = Duration.between(lastRunAt, Instant.now());
        if (sinceLast.toMinutes() < settings.getDelayMinutes()) {
            return;
        }
        runPayedSnapshot("scheduled");
    }

    private UUID runPayedSnapshot(String reason) {
        if (!running.compareAndSet(false, true)) {
            log.info("Skip PAYED enrichment run ({}): previous run is still active", reason);
            return null;
        }
        try {
            long sourceId = sourceManagementService.getOrCreateSource(SourceType.WHITE_ADMIN, "whiteadmin").getId();
            UUID taskId = UUID.randomUUID();
            EnrichmentRequest request = new EnrichmentRequest(
                    taskId,
                    sourceId,
                    EntityTypeEnum.ORDER,
                    null,
                    null,
                    OrderEnrichmentMode.PAYED_SNAPSHOT,
                    null,
                    -1L,
                    0L
            );
            enrichmentTaskManager.startEnrichment(request);
            activeTaskId = taskId;
            lastRunAt = Instant.now();
//            log.info("Started PAYED order enrichment run {} ({})", taskId, reason);
            return taskId;
        } finally {
            if (activeTaskId == null) {
                running.set(false);
            }
        }
    }

    private EnrichmentSchedulerSettingEntity createDefaultSettings() {
        EnrichmentSchedulerSettingEntity settings = new EnrichmentSchedulerSettingEntity();
        settings.setJobName(JOB_NAME);
        settings.setEnabled(false);
        settings.setDelayMinutes(DEFAULT_DELAY_MINUTES);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy("system");
        return settingRepository.save(settings);
    }
}
