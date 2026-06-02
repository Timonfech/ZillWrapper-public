package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StagesTimelineSectionRenderer implements ControlMessageSectionRenderer {

    @Override
    public boolean supports(ControlMessageContext context) {
        return context.rootExecution() != null
                && context.mode() == ControlMessageContext.RenderMode.DIAGNOSTIC;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String render(ControlMessageContext context) {
        List<OperationType> stages = context.children().stream()
                .map(OperationExecutionEntity::getOperationType)
                .distinct()
                .toList();
        if (stages.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add(context.msg("telegram.control.section.stages"));
        for (OperationType stage : stages) {
            Optional<OperationExecutionEntity> latest = context.children().stream()
                    .filter(child -> child.getOperationType() == stage)
                    .max(Comparator.comparing(OperationExecutionEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            OperationStatus status = latest.map(OperationExecutionEntity::getStatus).orElse(null);
            String icon = iconFor(status);
            String stageName = context.msg("telegram.operation.stage." + stage.name());
            String statusText = context.msg("telegram.operation.status." + (status == null ? "PENDING" : status.name()));
            lines.add(icon + " " + stageName + " — " + statusText);
        }
        return String.join("\n", lines);
    }

    private String iconFor(OperationStatus status) {
        if (status == null) return "⚪";
        return switch (status) {
            case RUNNING, RESUME -> "⏳";
            case DONE -> "✅";
            case PARTIALLY_DONE -> "⚠️";
            case FAILED -> "❌";
            case WAITING_INTERACTION -> "❓";
            case PAUSE -> "⏸️";
            case CANCELLED -> "🚫";
            default -> "⚪";
        };
    }
}
