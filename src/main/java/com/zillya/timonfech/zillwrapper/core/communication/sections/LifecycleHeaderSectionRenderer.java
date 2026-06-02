package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.links.ExternalLinkResolver;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LifecycleHeaderSectionRenderer implements ControlMessageSectionRenderer {

    private final OrderRepository orderRepository;
    private final ExternalLinkResolver externalLinkResolver;

    @Override
    public boolean supports(ControlMessageContext context) {
        return true;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String render(ControlMessageContext context) {
        OperationExecutionEntity execution = context.viewExecution();
        StringBuilder sb = new StringBuilder();
        sb.append(context.msg("telegram.control.operation")).append(" ").append(context.rootOperationId()).append("\n");
        if (context.mode() == ControlMessageContext.RenderMode.DIAGNOSTIC) {
            sb.append(context.msg("telegram.control.stage")).append(" ").append(execution.getOperationType()).append("\n");
        } else {
            Long orderId = context.resolvedOrderId();
            if (orderId != null) {
                sb.append("Order: #").append(orderId).append("\n");
                appendWhiteAdminLine(sb, orderId, context.msg("telegram.preview.whiteadmin"));
            }
        }
        sb.append(context.msg("telegram.control.status"))
                .append(" ")
                .append(statusIcon(execution.getStatus()))
                .append(" ")
                .append(context.msg("telegram.operation.status." + execution.getStatus().name()))
                .append("\n");
        if (context.mode() == ControlMessageContext.RenderMode.DIAGNOSTIC && execution.getHandlerName() != null) {
            sb.append(context.msg("telegram.control.handler")).append(" <code>").append(execution.getHandlerName()).append("</code>");
        }
        return sb.toString();
    }

    private String statusIcon(OperationStatus status) {
        if (status == null) return "ℹ️";
        return switch (status) {
            case RUNNING, RESUME -> "⏳";
            case DONE -> "✅";
            case PARTIALLY_DONE -> "⚠️";
            case FAILED -> "❌";
            case WAITING_INTERACTION -> "❓";
            case PAUSE -> "⏸️";
            case CANCELLED -> "🚫";
            default -> "ℹ️";
        };
    }

    private void appendWhiteAdminLine(StringBuilder sb, Long orderId, String label) {
        if (orderId == null) {
            return;
        }
        orderRepository.findById(orderId).ifPresent(order -> {
            Long waId = order.getWhiteAdminId();
            if (waId == null) {
                return;
            }
            String url = externalLinkResolver.resolveOrderLink(order)
                    .map(link -> link.url())
                    .orElse(null);
            if (url == null || url.isBlank()) {
                sb.append(label).append(": ").append(waId).append("\n");
                return;
            }
            sb.append(label).append(": ")
                    .append("<a href=\"").append(url).append("\">")
                    .append(waId)
                    .append("</a>")
                    .append("\n");
        });
    }
}
