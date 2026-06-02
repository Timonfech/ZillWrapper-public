package com.zillya.timonfech.zillwrapper.core.interactions.commands;

import com.zillya.timonfech.zillwrapper.core.communication.KeyPreviewFormatter;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.DinoKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.pipeline.PipelineDispatcher;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.pipeline.resend.ResendTarget;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.pipeline.OperationGraphRegistry;
import com.zillya.timonfech.zillwrapper.core.search.*;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommandInteractionService {
    private final SearchQueryParser queryParser;
    private final UnifiedSearchService unifiedSearchService;
    private final SearchSessionStore searchSessionStore;
    private final OperationExecutionService operationExecutionService;
    private final PipelineDispatcher pipelineDispatcher;
    private final OperationGraphRegistry operationGraphRegistry;
    private final TelegramOperationBindingRepository telegramBindingRepository;
    private final OrderItemRepository orderItemRepository;
    private final LicenseRepository licenseRepository;
    private final KeyPreviewFormatter keyPreviewFormatter;

    @Value("${interaction.bulk.max-candidates:200}")
    private int bulkMaxCandidates;
    @Value("${interaction.search.min-key-selector-length:4}")
    private int minKeySelectorLength;
    @Value("${interaction.license-mutation.special-confirm-threshold:50}")
    private int bulkMutationSpecialConfirmThreshold;

    public InteractionOutcome open(CommandIntent intent) {
        SearchEntityType entityType = intent.entityType() != null ? intent.entityType() : SearchEntityType.LICENSE;
        SearchQuery query = queryParser.parse(intent.payload());
        log.info("Search open: actorUserId={} chatId={} payload='{}' parsed={}",
                intent.actorUserId(),
                intent.chatId(),
                intent.payload(),
                query);
        if (query.entityType() != null) {
            entityType = query.entityType();
        }

        List<Long> pageIds = new ArrayList<>();
        List<String> pageTargetRefs = new ArrayList<>();
        List<String> views = new ArrayList<>();
        List<LicenseEntity> matchedLicenses = new ArrayList<>();
        OperationType action = intent.operationType();
        if (action == OperationType.RESEND_NOTIFICATION && intent.entityType() == null) {
            entityType = SearchEntityType.ORDER;
        }

        Optional<Long> correlatedOrderId = unifiedSearchService.resolveOrderIdByReplyCorrelation(intent.chatId(), intent.replyToMessageId());
        boolean queryEmpty = isQueryEmpty(query);
        boolean actionMode = isActionMode(action);
        String keyLengthValidationError = validateKeySelectorLength(query);
        if (keyLengthValidationError != null) {
            return InteractionOutcome.builder()
                    .type(InteractionOutcome.OutcomeType.ERROR)
                    .message(keyLengthValidationError)
                    .build();
        }
        if (queryEmpty && !actionMode) {
            log.warn("Search denied due to empty selectors: actorUserId={} chatId={} payload='{}'",
                    intent.actorUserId(), intent.chatId(), intent.payload());
            return InteractionOutcome.builder()
                    .type(InteractionOutcome.OutcomeType.ERROR)
                    .message("Search selectors are required.")
                    .build();
        }
        if (queryEmpty && actionMode && correlatedOrderId.isEmpty()) {
            log.warn("Action search denied due to empty selectors without reply correlation: actorUserId={} chatId={} op={} payload='{}'",
                    intent.actorUserId(), intent.chatId(), action, intent.payload());
            return InteractionOutcome.builder()
                    .type(InteractionOutcome.OutcomeType.ERROR)
                    .message("Provide search selectors or reply to an order/control message.")
                    .build();
        }

        if (correlatedOrderId.isPresent() && isActionMode(action)) {
            if (action == OperationType.RESEND_NOTIFICATION) {
                SearchQuery corr = SearchQuery.builder()
                        .entityType(SearchEntityType.ORDER)
                        .orderId(correlatedOrderId.get())
                        .productName(query.productName())
                        .build();
                List<OrderEntity> found = unifiedSearchService.resolve(SearchEntityType.ORDER, corr, OrderEntity.class);
                for (OrderEntity o : found) {
                    if (action == OperationType.RESEND_NOTIFICATION && !isEligibleResendOrder(o.getId())) {
                        continue;
                    }
                    pageIds.add(unifiedSearchService.internalId(SearchEntityType.ORDER, o, OrderEntity.class));
                    views.add(unifiedSearchService.render(SearchEntityType.ORDER, o, OrderEntity.class));
                    if (action == OperationType.RESEND_NOTIFICATION) {
                        pageTargetRefs.add(new ResendTarget(SearchEntityType.ORDER, o.getId(), null, null).encode());
                    }
                }
            } else {
            SearchQuery corr = SearchQuery.builder()
                    .entityType(SearchEntityType.LICENSE)
                    .orderId(correlatedOrderId.get())
                    .productName(query.productName())
                    .build();
            List<LicenseEntity> found = unifiedSearchService.resolve(SearchEntityType.LICENSE, corr, LicenseEntity.class);
            for (LicenseEntity l : found) {
                if (action == OperationType.DETACH_ACTIVATIONS && !(l.getKey() instanceof DinoKeyEntity)) {
                    continue;
                }
                pageIds.add(unifiedSearchService.internalId(SearchEntityType.LICENSE, l, LicenseEntity.class));
                views.add(unifiedSearchService.render(SearchEntityType.LICENSE, l, LicenseEntity.class));
                matchedLicenses.add(l);
                if (action == OperationType.RESEND_NOTIFICATION) {
                    pageTargetRefs.add(new ResendTarget(SearchEntityType.LICENSE, l.getOrderId(), l.getOrderItemId(), l.getId()).encode());
                }
            }
            }
        } else if (entityType == SearchEntityType.ORDER) {
            List<OrderEntity> found = unifiedSearchService.resolve(SearchEntityType.ORDER, query, OrderEntity.class);
            for (OrderEntity o : found) {
                if (action == OperationType.RESEND_NOTIFICATION && !isEligibleResendOrder(o.getId())) {
                    continue;
                }
                pageIds.add(unifiedSearchService.internalId(SearchEntityType.ORDER, o, OrderEntity.class));
                views.add(unifiedSearchService.render(SearchEntityType.ORDER, o, OrderEntity.class));
                if (action == OperationType.RESEND_NOTIFICATION) {
                    pageTargetRefs.add(new ResendTarget(SearchEntityType.ORDER, o.getId(), null, null).encode());
                }
            }
        } else {
            List<LicenseEntity> found = unifiedSearchService.resolve(SearchEntityType.LICENSE, query, LicenseEntity.class);
            for (LicenseEntity l : found) {
                if (action == OperationType.DETACH_ACTIVATIONS && !(l.getKey() instanceof DinoKeyEntity)) {
                    continue;
                }
                if (action == OperationType.RESEND_NOTIFICATION && !isEligibleResendLicense(l)) {
                    continue;
                }
                pageIds.add(unifiedSearchService.internalId(SearchEntityType.LICENSE, l, LicenseEntity.class));
                views.add(unifiedSearchService.render(SearchEntityType.LICENSE, l, LicenseEntity.class));
                matchedLicenses.add(l);
                if (action == OperationType.RESEND_NOTIFICATION) {
                    pageTargetRefs.add(new ResendTarget(SearchEntityType.LICENSE, l.getOrderId(), l.getOrderItemId(), l.getId()).encode());
                }
            }
        }

        if (pageIds.isEmpty()) {
            log.info("Search no match: actorUserId={} chatId={} entityType={} payload='{}'",
                    intent.actorUserId(),
                    intent.chatId(),
                    entityType,
                    intent.payload());
            return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.NO_MATCH).message("No matches found.").build();
        }

        String summaryView = null;
        if (entityType == SearchEntityType.LICENSE && pageIds.size() > 1 && !matchedLicenses.isEmpty()) {
            List<OrderItemEntity> allItems = matchedLicenses.stream()
                    .map(LicenseEntity::getOrderId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .flatMap(orderId -> orderItemRepository.findByOrderIdOrderByIdAsc(orderId).stream())
                    .toList();
            summaryView = keyPreviewFormatter.renderSearchSummary(matchedLicenses, allItems);
        }

        SearchSession session = SearchSession.builder()
                .chatId(intent.chatId())
                .userId(intent.actorUserId())
                .sourceId(intent.sourceId())
                .entityType(entityType)
                .actionType(action)
                .actionPayload(intent.payload())
                .summaryView(summaryView)
                .pageEntityIds(pageIds)
                .pageTargetRefs(pageTargetRefs.isEmpty() ? null : pageTargetRefs)
                .views(views)
                .pageIndex(0)
                .state(SearchSessionState.OPEN)
                .build();
        session = searchSessionStore.create(session);
        return InteractionOutcome.builder()
                .type(InteractionOutcome.OutcomeType.VIEW)
                .session(session)
                .actionMode(isActionMode(action))
                .actionLabel(resolveActionLabel(action, intent.payload()))
                .build();
    }

    public InteractionOutcome apply(String sessionId, Long actorUserId, InteractionAction action) {
        SearchSession session = searchSessionStore.get(sessionId).orElse(null);
        if (session == null) {
            return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.EXPIRED).message("Search session expired.").build();
        }
        if (actorUserId != null && session.getUserId() != null && !actorUserId.equals(session.getUserId())) {
            log.warn("Search session ownership mismatch: sessionId={} actorUserId={} ownerUserId={} chatId={}",
                    sessionId,
                    actorUserId,
                    session.getUserId(),
                    session.getChatId());
            return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.ERROR).message("Not your search session.").build();
        }
        switch (action) {
            case PREV -> session.setPageIndex(Math.max(0, session.getPageIndex() - 1));
            case NEXT -> session.setPageIndex(Math.min(session.getViews().size() - 1, session.getPageIndex() + 1));
            case CONFIRM_CURRENT -> {
                session.setBulkConfirmArmed(false);
                session.setWarningView(null);
                return executeAction(session, false);
            }
            case CONFIRM_ALL -> {
                return executeAction(session, true);
            }
            case CANCEL -> {
                session.setState(SearchSessionState.CANCELLED);
                searchSessionStore.remove(session.getSessionId());
                return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.STARTED).message("Cancelled.").build();
            }
        }
        return InteractionOutcome.builder()
                .type(InteractionOutcome.OutcomeType.VIEW)
                .session(session)
                .actionMode(isActionMode(session.getActionType()))
                .actionLabel(resolveActionLabel(session.getActionType(), session.getActionPayload()))
                .build();
    }

    public void attachUiMessage(String sessionId, Integer messageId) {
        searchSessionStore.getRaw(sessionId).ifPresent(s -> s.setUiMessageId(messageId));
    }

    public List<SearchSession> expireDueSessions(Instant now) {
        List<SearchSession> expired = searchSessionStore.collectExpiredOpen(now);
        for (SearchSession s : expired) {
            s.setState(SearchSessionState.EXPIRED);
            searchSessionStore.remove(s.getSessionId());
        }
        return expired;
    }

    private InteractionOutcome executeAction(SearchSession session, boolean all) {
        if (!isActionMode(session.getActionType())) {
            return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.ERROR).message("This session is read-only.").build();
        }
        List<Long> ids;
        List<String> targetRefs;
        if (all) {
            ids = session.getPageEntityIds();
            targetRefs = session.getPageTargetRefs();
            if (ids.size() > bulkMaxCandidates) {
                return InteractionOutcome.builder()
                        .type(InteractionOutcome.OutcomeType.ERROR)
                        .message("Too many candidates for confirm-all (" + ids.size() + "). Narrow your search.")
                        .build();
            }
            if (requiresSpecialBulkConfirmation(session, ids)) {
                if (!session.isBulkConfirmArmed()) {
                    session.setBulkConfirmArmed(true);
                    session.setWarningView("WARNING: bulk license change on "
                            + ids.size()
                            + " licenses. Press confirm all again to proceed.");
                    return InteractionOutcome.builder()
                            .type(InteractionOutcome.OutcomeType.VIEW)
                            .session(session)
                            .actionMode(true)
                            .actionLabel(resolveActionLabel(session.getActionType(), session.getActionPayload()))
                            .build();
                }
                session.setBulkConfirmArmed(false);
                session.setWarningView(null);
            }
        } else {
            ids = List.of(session.getPageEntityIds().get(session.getPageIndex()));
            if (session.getPageTargetRefs() != null && session.getPageTargetRefs().size() > session.getPageIndex()) {
                targetRefs = List.of(session.getPageTargetRefs().get(session.getPageIndex()));
            } else {
                targetRefs = null;
            }
        }
        if (session.getActionType() == OperationType.RESEND_NOTIFICATION) {
            for (int i = 0; i < ids.size(); i++) {
                Long fallbackOrderId = ids.get(i);
                ResendTarget target = null;
                if (targetRefs != null && i < targetRefs.size()) {
                    target = ResendTarget.decode(targetRefs.get(i));
                }
                Long orderId = target != null ? target.orderId() : fallbackOrderId;
                if (orderId == null) {
                    continue;
                }
                OrderOperationContext context = new OrderOperationContext(
                        session.getSourceId(),
                        null,
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        null
                );
                context.setOrderId(orderId);
                context.setCurrentStage(session.getActionType());
                String scopedPayload = session.getActionPayload();
                if (target != null) {
                    scopedPayload = mergePayload(scopedPayload, "rid_scope=entity:" + target.entityType().name().toLowerCase());
                    if (target.orderItemId() != null) {
                        scopedPayload = mergePayload(scopedPayload, "rid_order_item_id=" + target.orderItemId());
                    }
                    if (target.licenseId() != null) {
                        scopedPayload = mergePayload(scopedPayload, "rid_license_id=" + target.licenseId());
                    }
                }
                context.setCommandPayload(scopedPayload);
                context.setInitiatorUserId(session.getUserId());
                ExecutionPlan executionPlan = operationGraphRegistry.buildExecutionPlan(session.getActionType(), context);
                context.replacePipelinePlan(executionPlan.steps().stream().map(step -> step.stageType()).toList());
                operationExecutionService.createParentOperation(context, session.getActionType());
                operationExecutionService.ensurePlannedStages(context.getOperationId(), context, executionPlan);
                ensureTelegramBindingForActionOperation(context.getOperationId(), session);
                pipelineDispatcher.dispatch(context);
            }
        } else {
            OrderOperationContext context = new OrderOperationContext(
                    session.getSourceId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null
            );
            context.setCurrentStage(session.getActionType());
            String lidPayload = "lid=" + ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            String mergedPayload = session.getActionType() == OperationType.MODIFY_STATUS
                    ? mergePayload(extractStatusOnlyPayload(session.getActionPayload()), lidPayload)
                    : mergePayload(session.getActionPayload(), lidPayload);
            context.setCommandPayload(mergedPayload);
            context.setInitiatorUserId(session.getUserId());
            ExecutionPlan executionPlan = operationGraphRegistry.buildExecutionPlan(session.getActionType(), context);
            context.replacePipelinePlan(executionPlan.steps().stream().map(step -> step.stageType()).toList());
            operationExecutionService.createParentOperation(context, session.getActionType());
            operationExecutionService.ensurePlannedStages(context.getOperationId(), context, executionPlan);
            ensureTelegramBindingForActionOperation(context.getOperationId(), session);
            pipelineDispatcher.dispatch(context);
        }

        session.setState(SearchSessionState.COMPLETED);
        searchSessionStore.remove(session.getSessionId());
        String msg;
        if (session.getActionType() == OperationType.RESEND_NOTIFICATION) {
            Long displayedOrderId = ids.isEmpty() ? null : ids.getFirst();
            if (targetRefs != null && !targetRefs.isEmpty()) {
                try {
                    ResendTarget displayed = ResendTarget.decode(targetRefs.getFirst());
                    if (displayed.orderId() != null) {
                        displayedOrderId = displayed.orderId();
                    }
                } catch (Exception ignored) {
                }
            }
            msg = all
                    ? "Action accepted. Starting resend for " + ids.size() + " order(s)."
                    : "Action accepted. Starting resend for order #" + displayedOrderId + ".";
        } else {
            msg = all
                    ? "Action accepted. Starting " + ids.size() + " license update(s)."
                    : "Action accepted. Starting license update for #" + ids.getFirst() + ".";
        }
        return InteractionOutcome.builder().type(InteractionOutcome.OutcomeType.STARTED).message(msg).build();
    }

    private boolean isActionMode(OperationType type) {
        return type == OperationType.MODIFY_STATUS
                || type == OperationType.DETACH_ACTIVATIONS
                || type == OperationType.RESEND_NOTIFICATION
                || type == OperationType.SUSPENSION;
    }

    private String resolveActionLabel(OperationType type, String payload) {
        if (type == OperationType.DETACH_ACTIVATIONS) {
            return "detach";
        }
        if (type == OperationType.MODIFY_STATUS) {
            String p = payload == null ? "" : payload.toLowerCase();
            if (p.contains("status=allow") || p.contains("status=allowed")) {
                return "allow";
            }
            return "block";
        }
        if (type == OperationType.RESEND_NOTIFICATION) {
            return "resend";
        }
        return "action";
    }

    private String mergePayload(String base, String suffix) {
        if (base == null || base.isBlank()) {
            return suffix;
        }
        return base.trim() + " " + suffix;
    }

    private String extractStatusOnlyPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String lower = payload.toLowerCase();
        if (lower.contains("status=allow") || lower.contains("status=allowed")) {
            return "status=allow";
        }
        if (lower.contains("status=block") || lower.contains("status=blocked")) {
            return "status=blocked";
        }
        return "";
    }

    private void ensureTelegramBindingForActionOperation(java.math.BigInteger operationId, SearchSession session) {
        if (operationId == null || session == null || session.getChatId() == null) {
            return;
        }
        if (telegramBindingRepository.findByOperationId(operationId).isPresent()) {
            return;
        }
        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(operationId);
        binding.setChatId(session.getChatId());
        binding.setControlMessageId(session.getUiMessageId());
        binding.setQuestionQueueJson("[]");
        binding.setInteractionDeliveryStatus("NOT_SENT");
        telegramBindingRepository.save(binding);
    }

    private boolean isQueryEmpty(SearchQuery q) {
        return q.orderId() == null
                && q.woid() == null
                && q.wzid() == null
                && q.wid2() == null
                && q.pid() == null
                && q.lex() == null
                && q.kid() == null
                && (q.kon() == null || q.kon().isBlank())
                && (q.kof() == null || q.kof().isBlank())
                && (q.comment() == null || q.comment().isBlank())
                && (q.productName() == null || q.productName().isBlank());
    }

    private boolean isEligibleResendOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        boolean hasKeys = licenseRepository.findByOrderId(orderId).stream().anyMatch(this::hasAnyKeyValue);
        if (!hasKeys) {
            return false;
        }
        Set<ItemProcessingStatus> allowed = Set.of(ItemProcessingStatus.DELIVERED, ItemProcessingStatus.DELIVERY_FAILED);
        return orderItemRepository.findByOrderId(orderId).stream()
                .map(item -> item.getProcessingStatus())
                .anyMatch(allowed::contains);
    }

    private boolean isEligibleResendLicense(LicenseEntity l) {
        if (l == null || l.getOrderId() == null || !hasAnyKeyValue(l)) {
            return false;
        }
        return isEligibleResendOrder(l.getOrderId());
    }

    private boolean hasAnyKeyValue(LicenseEntity l) {
        if (l.getKey() == null) {
            return false;
        }
        String online = l.getKey().getOnlineKey();
        String offline = l.getKey().getOfflineKey();
        return (online != null && !online.isBlank()) || (offline != null && !offline.isBlank());
    }

    private String validateKeySelectorLength(SearchQuery q) {
        if (q.kon() != null && !q.kon().isBlank() && q.kon().trim().length() < minKeySelectorLength) {
            return "Selector 'kon' must be at least " + minKeySelectorLength + " characters.";
        }
        if (q.kof() != null && !q.kof().isBlank() && q.kof().trim().length() < minKeySelectorLength) {
            return "Selector 'kof' must be at least " + minKeySelectorLength + " characters.";
        }
        return null;
    }

    private boolean requiresSpecialBulkConfirmation(SearchSession session, List<Long> ids) {
        return session.getEntityType() == SearchEntityType.LICENSE
                && ids != null
                && ids.size() > bulkMutationSpecialConfirmThreshold
                && (session.getActionType() == OperationType.MODIFY_STATUS
                || session.getActionType() == OperationType.DETACH_ACTIVATIONS
                || session.getActionType() == OperationType.SUSPENSION);
    }
}
