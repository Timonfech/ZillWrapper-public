package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionKind;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OperationExecutionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderResendTargetResolver implements ResendTargetResolver {

    private final OrderRepository orderRepository;
    private final TelegramOperationBindingRepository bindingRepository;
    private final OperationExecutionRepository operationExecutionRepository;

    @Override
    public boolean supports(EntityTypeEnum entityType) {
        return entityType == EntityTypeEnum.ORDER;
    }

    @Override
    public ResendResolveResult resolve(ResendLookupContext context) {
        log.info("OrderResendTargetResolver start: explicitOrderId={} whiteAdminId={} portalId={} userComment='{}' chatId={} controlMessageId={}",
                context.explicitOrderId(),
                context.whiteAdminId(),
                context.portalId(),
                context.userComment(),
                context.chatId(),
                context.controlMessageId());
        if (context.chatId() != null && context.controlMessageId() != null) {
            ResendResolveResult byReply = resolveByTelegramReplyCorrelation(context.chatId(), context.controlMessageId());
            if (byReply.status() == ResendResolveStatus.FOUND || byReply.status() == ResendResolveStatus.AMBIGUOUS) {
                return byReply;
            }
            log.warn("Resolver binding lookup miss chatId={} controlMessageId={}, fallback to reference search",
                    context.chatId(),
                    context.controlMessageId());
        }

        if (context.explicitOrderId() != null) {
            ResendResolveResult byId = resolveUnique(orderRepository.findById(context.explicitOrderId()),
                    "orderId=" + context.explicitOrderId(), "explicit-order-id");
            if (byId.status() == ResendResolveStatus.FOUND || byId.status() == ResendResolveStatus.AMBIGUOUS) {
                return byId;
            }
        }

        if (context.whiteAdminId() != null) {
            ResendResolveResult byWhiteAdmin = resolveUnique(orderRepository.findAllByWhiteAdminId(context.whiteAdminId()),
                    "whiteAdminId=" + context.whiteAdminId(), "white-admin-id");
            if (byWhiteAdmin.status() == ResendResolveStatus.FOUND || byWhiteAdmin.status() == ResendResolveStatus.AMBIGUOUS) {
                return byWhiteAdmin;
            }
        }

        if (context.portalId() != null) {
            ResendResolveResult byPortal = resolveUnique(orderRepository.findAllByPortalId(context.portalId()),
                    "portalId=" + context.portalId(), "portal-id");
            if (byPortal.status() == ResendResolveStatus.FOUND || byPortal.status() == ResendResolveStatus.AMBIGUOUS) {
                return byPortal;
            }
        }

        if (context.userComment() != null && !context.userComment().isBlank()) {
            ResendResolveResult byComment = resolveUnique(
                    orderRepository.findAllByUserCommentNormalized(context.userComment().trim()),
                    "userComment=" + context.userComment().trim(),
                    "comment");
            if (byComment.status() == ResendResolveStatus.FOUND || byComment.status() == ResendResolveStatus.AMBIGUOUS) {
                return byComment;
            }
        }

        return ResendResolveResult.notFound("No matching order found by id/reference/comment/control message");
    }

    private ResendResolveResult resolveUnique(Optional<com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity> orderOpt,
                                              String criteria,
                                              String source) {
        return orderOpt.map(order -> ResendResolveResult.found(new ResolvedResendTarget(
                        EntityTypeEnum.ORDER,
                        order.getId(),
                        null,
                        source
                )))
                .orElseGet(() -> ResendResolveResult.notFound("No order found by " + criteria));
    }

    private ResendResolveResult resolveUnique(List<com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity> orders,
                                              String criteria,
                                              String source) {
        Set<Long> ids = new LinkedHashSet<>();
        for (com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity order : orders) {
            ids.add(order.getId());
        }
        if (ids.isEmpty()) {
            return ResendResolveResult.notFound("No order found by " + criteria);
        }
        if (ids.size() > 1) {
            log.warn("Ambiguous resend target by {}. matchedOrderIds={}", criteria, ids);
            return ResendResolveResult.ambiguous("Multiple orders found by " + criteria + ": " + ids);
        }
        Long only = ids.iterator().next();
        return ResendResolveResult.found(new ResolvedResendTarget(EntityTypeEnum.ORDER, only, null, source));
    }

    private Optional<ResolvedResendTarget> resolveFromBinding(TelegramOperationBindingEntity binding) {
        if (binding.getOperationId() == null) {
            return Optional.empty();
        }
        return resolveFromOperationId(binding.getOperationId());
    }

    private Optional<ResolvedResendTarget> resolveFromOperationId(BigInteger anyOperationId) {
        Optional<OperationExecutionEntity> opOpt = operationExecutionRepository.findById(anyOperationId);
        if (opOpt.isEmpty()) {
            return Optional.empty();
        }
        OperationExecutionEntity op = opOpt.get();
        BigInteger rootOperationId = op.getParentId() != null ? op.getParentId() : op.getId();
        if (op.getExecutionKind() == OperationExecutionKind.STAGE
                && op.getOperationType() == OperationType.ORDER_CREATION
                && op.getEntityId() != null) {
            return Optional.of(new ResolvedResendTarget(
                    EntityTypeEnum.ORDER,
                    op.getEntityId(),
                    rootOperationId,
                    "operation-stage"
            ));
        }

        List<OperationExecutionEntity> children = operationExecutionRepository.findByParentId(rootOperationId);
        for (OperationExecutionEntity child : children) {
            if (child.getExecutionKind() == OperationExecutionKind.STAGE
                    && child.getOperationType() == OperationType.ORDER_CREATION
                    && child.getEntityId() != null) {
                return Optional.of(new ResolvedResendTarget(
                        EntityTypeEnum.ORDER,
                        child.getEntityId(),
                        rootOperationId,
                        "operation-linkage"
                ));
            }
        }
        return Optional.empty();
    }

    private ResendResolveResult resolveByTelegramReplyCorrelation(Long chatId, Integer replyToMessageId) {
        log.info("Resolver attempting strict reply correlation: chatId={} replyToMessageId={}", chatId, replyToMessageId);
        List<TelegramOperationBindingEntity> candidates = new ArrayList<>();
        for (TelegramOperationBindingEntity binding : bindingRepository.findByChatId(chatId)) {
            if (binding.getControlMessageId() != null && binding.getControlMessageId().equals(replyToMessageId)) {
                candidates.add(binding);
            } else if (binding.getPreviewMessageId() != null && binding.getPreviewMessageId().equals(replyToMessageId)) {
                candidates.add(binding);
            }
        }

        if (candidates.isEmpty()) {
            return ResendResolveResult.notFound("No telegram binding found by reply message");
        }

        Set<Long> orderIds = new LinkedHashSet<>();
        Set<BigInteger> rootOpIds = new LinkedHashSet<>();
        for (TelegramOperationBindingEntity binding : candidates) {
            Optional<ResolvedResendTarget> target = resolveFromBinding(binding);
            target.ifPresent(resolved -> {
                orderIds.add(resolved.entityId());
                if (resolved.rootOperationId() != null) {
                    rootOpIds.add(resolved.rootOperationId());
                }
            });
        }

        if (orderIds.isEmpty()) {
            return ResendResolveResult.notFound("Telegram reply is linked, but order is not resolved yet");
        }
        if (orderIds.size() > 1) {
            return ResendResolveResult.ambiguous("Multiple orders mapped by reply message: " + orderIds);
        }
        Long orderId = orderIds.iterator().next();
        BigInteger rootOpId = rootOpIds.isEmpty() ? null : rootOpIds.iterator().next();
        return ResendResolveResult.found(new ResolvedResendTarget(
                EntityTypeEnum.ORDER,
                orderId,
                rootOpId,
                "telegram-reply"
        ));
    }
}
