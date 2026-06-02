package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import org.springframework.stereotype.Component;

@Component
public class InteractionSectionRenderer implements ControlMessageSectionRenderer {

    @Override
    public boolean supports(ControlMessageContext context) {
        return context.children() != null
                && !context.children().isEmpty()
                && context.mode() == ControlMessageContext.RenderMode.DIAGNOSTIC;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String render(ControlMessageContext context) {
        long waitingCount = context.children().stream()
                .filter(child -> child.getStatus() == OperationStatus.WAITING_INTERACTION)
                .count();
        if (waitingCount <= 0) {
            return "";
        }
        return "❓ " + context.msg("telegram.control.open_questions") + " " + waitingCount;
    }
}
