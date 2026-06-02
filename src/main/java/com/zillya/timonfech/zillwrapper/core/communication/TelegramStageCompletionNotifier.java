package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.pipeline.StageCompletionNotification;
import com.zillya.timonfech.zillwrapper.core.pipeline.StageCompletionNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramStageCompletionNotifier implements StageCompletionNotifier {

    private final TelegramControlMessageService telegramControlMessageService;

    @Qualifier("pipelineTaskExecutor")
    private final Executor pipelineTaskExecutor;

    @Override
    public void notifyStageCompletion(StageCompletionNotification notification) {
        if (notification == null || !notification.interactiveEnabled()) {
            return;
        }
        if (notification.status() != OperationStatus.PARTIALLY_DONE
                && notification.status() != OperationStatus.FAILED) {
            return;
        }
        if (notification.summary() == null || notification.summary().isBlank()) {
            return;
        }
        if (notification.operationType() == OperationType.LEGACY_SYNC
                && notification.summary().contains("Legacy sync skipped: order has no whiteAdminId")) {
            return;
        }
        pipelineTaskExecutor.execute(() -> {
            try {
                telegramControlMessageService.applyLateStageWarningToFinalMessage(
                        notification.parentOperationId(),
                        notification.stageExecutionId(),
                        notification.operationType(),
                        notification.status(),
                        notification.summary()
                );
                telegramControlMessageService.refreshControlMessage(notification.parentOperationId());
                log.info("stage_completion_notified mode=async stageExecId={} parentOpId={} stage={} status={}",
                        notification.stageExecutionId(),
                        notification.parentOperationId(),
                        notification.operationType(),
                        notification.status());
            } catch (Exception ex) {
                log.warn("Failed to notify late stage completion stageExecId={} parentOpId={}: {}",
                        notification.stageExecutionId(),
                        notification.parentOperationId(),
                        ex.getMessage());
            }
        });
    }
}
