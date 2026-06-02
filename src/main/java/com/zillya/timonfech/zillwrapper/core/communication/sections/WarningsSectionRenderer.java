package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WarningsSectionRenderer implements ControlMessageSectionRenderer {

    @Override
    public boolean supports(ControlMessageContext context) {
        return context.children() != null
                && context.mode() == ControlMessageContext.RenderMode.DIAGNOSTIC;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String render(ControlMessageContext context) {
        List<String> warnings = context.children().stream()
                .filter(child -> child.getStatus() == OperationStatus.PARTIALLY_DONE
                        || child.getStatus() == OperationStatus.FAILED)
                .filter(child -> child.getErrorMessage() != null && !child.getErrorMessage().isBlank())
                .map(child -> icon(child.getStatus()) + " "
                        + context.msg("telegram.operation.stage." + child.getOperationType().name())
                        + ": " + child.getErrorMessage())
                .toList();
        if (warnings.isEmpty()) {
            return "";
        }
        return context.msg("telegram.control.section.warnings") + "\n" + String.join("\n", warnings);
    }

    private String icon(OperationStatus status) {
        return status == OperationStatus.PARTIALLY_DONE ? "⚠️" : "❌";
    }
}
