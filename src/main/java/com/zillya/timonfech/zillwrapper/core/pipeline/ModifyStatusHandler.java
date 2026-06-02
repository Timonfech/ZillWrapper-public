package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.pipeline.actions.LicenseActionExecutor;
import com.zillya.timonfech.zillwrapper.core.pipeline.actions.LicenseBlockOutcome;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModifyStatusHandler implements OperationHandler<IOperationContext> {

    private final LicenseTargetResolverService targetResolver;
    private final LicenseRepository licenseRepository;
    private final List<LicenseActionExecutor> actionExecutors;

    @Override
    public String name() {
        return "MODIFY_STATUS";
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.MODIFY_STATUS
                && context instanceof OrderOperationContext;
    }

    @Override
    @OperationStep(type = OperationType.MODIFY_STATUS, stepProps = {OperationStep.Props.START, OperationStep.Props.FINAL})
    public OperationResult<?> handle(IOperationContext context) {
        OrderOperationContext orderCtx = (OrderOperationContext) context;
        LicenseStatus targetStatus = resolveTargetStatus(orderCtx.getCommandPayload());
        if (targetStatus == null) {
            return OperationResult.fail("Target status is required. Use status=allow or status=blocked", false);
        }
        List<LicenseEntity> targets = targetResolver.resolveTargets(orderCtx);
        if (targets.isEmpty()) {
            return OperationResult.fail("No licenses found for block input", false);
        }

        int updated = 0;
        int externalApplied = 0;
        int unsupported = 0;
        int failed = 0;
        int unchanged = 0;
        List<Long> updatedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        List<String> updatedDetails = new ArrayList<>();
        List<String> failedDetails = new ArrayList<>();
        List<String> unchangedDetails = new ArrayList<>();
        for (LicenseEntity license : targets) {
            if (license.getStatus() == targetStatus) {
                unchanged++;
                unchangedDetails.add("#" + license.getId() + "[" + keySummary(license) + ";product=" + productSummary(license) + "]=unchanged");
                continue;
            }
            LicenseActionExecutor executor = actionExecutors.stream()
                    .filter(e -> e.supports(license))
                    .findFirst()
                    .orElse(null);
            if (executor == null) {
                unsupported++;
                orderCtx.addWarning("No status executor for license #" + license.getId());
                continue;
            }
            LicenseBlockOutcome outcome = executor.updateStatus(license, targetStatus);
            if (outcome.error() != null && !outcome.error().isBlank()) {
                failed++;
                failedIds.add(license.getId());
                orderCtx.addWarning("license #" + license.getId() + " [" + keySummary(license) + "] failed: " + outcome.error());
                failedDetails.add("#" + license.getId() + "[" + keySummary(license) + ";product=" + productSummary(license) + "]=failed(" + sanitize(outcome.error()) + ")");
                log.warn("Modify status failed for licenseId={} targetStatus={} reason={}",
                        license.getId(), targetStatus, outcome.error());
                continue;
            }
            license.setStatus(targetStatus);
            if (outcome.externalApplied()) {
                externalApplied++;
            }
            if (outcome.warning() != null && !outcome.warning().isBlank()) {
                orderCtx.addWarning(outcome.warning());
            }
            updated++;
            updatedIds.add(license.getId());
            updatedDetails.add("#" + license.getId() + "[" + keySummary(license) + ";product=" + productSummary(license) + "]=success(" + targetStatus + ")");
        }

        if (updated > 0) {
            licenseRepository.saveAll(targets);
        }

        if (updated == 0 && (failed > 0 || unsupported > 0)) {
            return OperationResult.fail("Status update failed for all targets. failed=" + failed + ", unsupported=" + unsupported + ", unchanged=" + unchanged, false);
        }

        String summary = "Status update completed: target=" + targetStatus
                + ", updated=" + updated
                + ", externalApplied=" + externalApplied
                + ", failed=" + failed
                + ", unsupported=" + unsupported
                + ", unchanged=" + unchanged
                + ", total=" + targets.size();
        String statusResult = "STATUS_RESULT target=" + targetStatus
                + " total=" + targets.size()
                + " updated=" + updated
                + " failed=" + failed
                + " unsupported=" + unsupported
                + " unchanged=" + unchanged
                + " updatedIds=" + toCsv(updatedIds)
                + " failedIds=" + toCsv(failedIds)
                + " detail_updated=" + toCsvText(updatedDetails)
                + " detail_failed=" + toCsvText(failedDetails)
                + " detail_unchanged=" + toCsvText(unchangedDetails);
        orderCtx.addWarning(statusResult);
        log.info(summary);
        return OperationResult.ok(statusResult);
    }

    private String toCsv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "-";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String toCsvText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return String.join("|", values);
    }

    private String keySummary(LicenseEntity license) {
        if (license == null || license.getKey() == null) {
            return "-";
        }
        String online = normalize(license.getKey().getOnlineKey());
        String offline = normalize(license.getKey().getOfflineKey());
        if (online == null && offline == null) {
            return "-";
        }
        if (online != null && offline != null) {
            return "ON=" + online + "/OFF=" + offline;
        }
        if (online != null) {
            return "ON=" + online;
        }
        return "OFF=" + offline;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = KeyMarkersUtils.removeMarkers(value).trim();
        trimmed = trimmed.replaceAll("\\s+", "");
        trimmed = trimmed.replaceAll("[^A-Za-z0-9]", "");
        if (trimmed.isBlank()) {
            return null;
        }
        int len = Math.min(8, trimmed.length());
        return trimmed.substring(0, len);
    }

    private String productSummary(LicenseEntity license) {
        if (license == null) {
            return "-";
        }
        if (license.getBrandId() == null || license.getProductId() == null) {
            return "-";
        }
        return license.getBrandId() + "/" + license.getProductId();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace(',', ';')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", "_")
                .trim();
    }

    private LicenseStatus resolveTargetStatus(String payload) {
        if (payload == null || payload.isBlank()) {
            return LicenseStatus.BLOCKED;
        }
        String lower = payload.toLowerCase();
        if (lower.contains("status=allow") || lower.contains("status=allowed")) {
            return LicenseStatus.ALLOWED;
        }
        if (lower.contains("status=block") || lower.contains("status=blocked")) {
            return LicenseStatus.BLOCKED;
        }
        return null;
    }
}
