package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrichmentProgressSectionRenderer implements ControlMessageSectionRenderer {

    private final EnrichmentProgressRegistry progressRegistry;

    @Override
    public boolean supports(ControlMessageContext context) {
        return context.rootExecution() != null
                && context.rootExecution().getOperationType() == OperationType.ENTITY_ENRICHMENT;
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public String render(ControlMessageContext context) {
        return progressRegistry.findByOperationId(context.rootOperationId())
                .map(progress -> {
                    long total = progress.total();
                    long processed = progress.processed();
                    long safeTotal = total <= 0 ? 0 : total;
                    long clampedProcessed = safeTotal <= 0 ? processed : Math.min(processed, safeTotal);
                    long percent = safeTotal <= 0 ? 0 : (clampedProcessed * 100) / safeTotal;
                    String mode = progress.single()
                            ? context.msg("telegram.enrichment.progress.mode.single")
                            : context.msg("telegram.enrichment.progress.mode.range");
                    String current = progress.currentExternalId() == null ? "-" : String.valueOf(progress.currentExternalId());
                    String totalDisplay = safeTotal <= 0 ? "-" : String.valueOf(safeTotal);
                    String progressBar = buildProgressBar(percent);
                    return context.msg("telegram.enrichment.progress.title") + "\n"
                            + context.msg("telegram.enrichment.progress.percent") + " " + progressBar + " " + percent + "%\n"
                            + context.msg("telegram.enrichment.progress.processed") + " " + clampedProcessed + "/" + totalDisplay + "\n"
                            + context.msg("telegram.enrichment.progress.licenses") + " " + progress.licensesProcessed() + "/" + totalDisplay + "\n"
                            + context.msg("telegram.enrichment.progress.activations") + " " + progress.activationsProcessed() + "/" + progress.activationsRequired()
                            + " (failed: " + progress.activationsFailed() + ", skipped: " + progress.activationsSkipped() + ")\n"
                            + context.msg("telegram.enrichment.progress.current") + " " + current + "\n"
                            + context.msg("telegram.enrichment.progress.mode") + " " + mode;
                })
                .orElseGet(() -> {
                    if (context.rootExecution().getStatus() == OperationStatus.DONE) {
                        return context.msg("telegram.enrichment.progress.final.done");
                    }
                    if (context.rootExecution().getStatus() == OperationStatus.CANCELLED) {
                        return context.msg("telegram.enrichment.progress.final.cancelled");
                    }
                    if (context.rootExecution().getStatus() == OperationStatus.FAILED) {
                        return context.msg("telegram.enrichment.progress.final.failed");
                    }
                    return "";
                });
    }

    private String buildProgressBar(long percent) {
        int length = 10;
        long bounded = Math.max(0, Math.min(100, percent));
        int filled = (int) ((bounded * length) / 100);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        return sb.toString();
    }
}
