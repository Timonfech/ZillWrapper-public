package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.Answer;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.StringsListAnswer;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.YesNoAnswer;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.DuplicateQuestion;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.NewStringsQuestion;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.interfaces.ResumableOperationHandler;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodNormalizer;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.security.OrderSecurityService;
import com.zillya.timonfech.zillwrapper.core.services.OrderProcessingService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.ContactManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationHandler implements OperationHandler<IOperationContext>,
        ResumableOperationHandler {

    private final OrderProcessingService orderProcessingService;
    private final OrderSecurityService orderSecurityService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ContactManagementService contactManagementService;
    private final ObjectMapper objectMapper;
    private final BusinessPeriodNormalizer periodNormalizer;
    private final OperationRuntimeRegistry runtimeRegistry;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final String QUESTION_KEY_EMAIL = "email";
    private static final String QUESTION_KEY_ORDER_ID = "orderId";

    @Override
    public String name() {
        return "ORDER_CREATION";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.ORDER_CREATION;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.ORDER_CREATION && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.ORDER_CREATION, stepProps = {OperationStep.Props.CRUCIAL})
    @Override
    public OperationResult<?> handle(IOperationContext context) throws OperationCancelledException {
        OrderOperationContext orderCtx = asOrderContext(context).orElse(null);
        if (orderCtx == null) {
            return OperationResult.fail("ORDER_CREATION requires OrderOperationContext", false);
        }

        log.info("Starting Security Audit for operation: {}", orderCtx.getOperationId());
        
        Long userId = orderCtx.getInitiatorUserId();
        if (userId == null) {
            throw new SecurityException("No initiator user found in context");
        }
        
        com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SecurityException("Initiator user not found"));

        // Perform Security Checks
        orderSecurityService.checkGeneralAccess(user);
        orderSecurityService.checkOrder(orderCtx);
        orderSecurityService.checkOrderQuota(user, orderCtx);

        if (!orderCtx.isSkipDuplicateCheck()) {
            Optional<Long> duplicateOrderId = findDuplicateOrderId(orderCtx);
            if (duplicateOrderId.isPresent()) {
                throw new NeedUserInteractionException(new DuplicateQuestion(
                        "ORDER",
                        resolveQuestionReferenceId(orderCtx),
                        resolveQuestionCurrentReference(orderCtx),
                        "ORDER",
                        duplicateOrderId.get()
                ));
            }
        }

        log.info("Security Audit passed. Creating order in DB.");
        
        Long orderId = orderProcessingService.createOrder(orderCtx);
        orderCtx.setOrderId(orderId);

        // Reserve Quota (Soft Deduction)
        orderSecurityService.reserveQuota(userId, orderId);

        if (!isValidEmail(orderCtx.primaryEmail())) {
            throw new NeedUserInteractionException(new NewStringsQuestion(Map.of(
                    QUESTION_KEY_EMAIL, safe(orderCtx.primaryEmail()),
                    QUESTION_KEY_ORDER_ID, String.valueOf(orderId)
            )));
        }
        
        return OperationResult.ok(null);
    }

    @Override
    public boolean supports(OperationExecutionEntity stageExecution, Question question, Answer answer) {
        if (stageExecution == null || stageExecution.getOperationType() != OperationType.ORDER_CREATION) {
            return false;
        }
        if (question instanceof NewStringsQuestion newStringsQuestion) {
            return newStringsQuestion.dataKeyValue() != null
                    && newStringsQuestion.dataKeyValue().containsKey(QUESTION_KEY_EMAIL)
                    && answer instanceof StringsListAnswer;
        }
        return question instanceof DuplicateQuestion && answer instanceof YesNoAnswer;
    }

    @Override
    @Transactional
    public OperationResult<?> resume(OperationExecutionEntity stageExecution, Question question, Answer answer) {
        if (question instanceof DuplicateQuestion duplicateQuestion && answer instanceof YesNoAnswer yesNoAnswer) {
            if (!yesNoAnswer.confirmed()) {
                return OperationResult.fail("Duplicate order rejected by user", false);
            }
            if (duplicateQuestion.duplicateEntityId() == null) {
                return OperationResult.fail("Duplicate order id is missing", false);
            }
            OrderEntity existingOrder = orderRepository.findByIdWithItems(duplicateQuestion.duplicateEntityId()).orElse(null);
            if (existingOrder == null) {
                return OperationResult.fail("Duplicate order not found: " + duplicateQuestion.duplicateEntityId(), false);
            }
            if (stageExecution.getParentId() == null) {
                return OperationResult.fail("Missing parent operation id for duplicate append", false);
            }
            OrderOperationContext orderCtx = runtimeRegistry.load(stageExecution.getParentId()).orElse(null);
            if (orderCtx == null || orderCtx.getItemSpecs() == null || orderCtx.getItemSpecs().isEmpty()) {
                return OperationResult.fail("No order context item specs found for duplicate append", false);
            }
            for (var spec : orderCtx.getItemSpecs()) {
                com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity item = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity();
                item.setOrderId(existingOrder.getId());
                item.setOrder(existingOrder);
                item.setProductBrandId(spec.product().brandId());
                item.setProductId(spec.product().productId());
                item.setPcPerLicense(spec.computers());
                item.setCount(spec.count());
                item.setBusinessPeriod(spec.period());
                item.setOutputTypes(spec.outputTypes());
                item.setKeyTypes(spec.keyTypes());
                item.setServerNumber(spec.options() == null ? null : spec.options().serverNumber());
                item.setProcessingStatus(com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus.PENDING);
                existingOrder.getItems().add(item);
            }
            if (existingOrder.getOrderStatus() != OrderStatus.PAYED) {
                existingOrder.setOrderStatus(OrderStatus.PAYED);
            }
            if (existingOrder.getWhiteAdminId() == null && orderCtx.getWhiteAdminId() != null) {
                existingOrder.setWhiteAdminId(orderCtx.getWhiteAdminId());
            }
            if (existingOrder.getPortalId() == null && orderCtx.getPortalId() != null) {
                existingOrder.setPortalId(orderCtx.getPortalId());
            }
            if ((existingOrder.getUserComment() == null || existingOrder.getUserComment().isBlank())
                    && orderCtx.getUserComment() != null
                    && !orderCtx.getUserComment().isBlank()) {
                existingOrder.setUserComment(orderCtx.getUserComment());
            }
            orderRepository.save(existingOrder);
            stageExecution.setEntityId(existingOrder.getId());
            if (stageExecution.getParentId() != null) {
                runtimeRegistry.patchAfterStageResult(
                        stageExecution.getParentId(),
                        existingOrder.getId(),
                        null,
                        true
                );
            }
            return OperationResult.ok(null);
        }

        if (!(question instanceof NewStringsQuestion newStringsQuestion) || !(answer instanceof StringsListAnswer stringsListAnswer)) {
            return OperationResult.fail("Unsupported resume payload for ORDER_CREATION", false);
        }

        String correctedEmail = extractFirst(stringsListAnswer.values());
        if (!isValidEmail(correctedEmail)) {
            throw new NeedUserInteractionException(new NewStringsQuestion(Map.of(
                    QUESTION_KEY_EMAIL, safe(correctedEmail),
                    QUESTION_KEY_ORDER_ID, safe(newStringsQuestion.dataKeyValue().get(QUESTION_KEY_ORDER_ID))
            )));
        }

        Long orderId = parseLong(newStringsQuestion.dataKeyValue().get(QUESTION_KEY_ORDER_ID)).orElse(stageExecution.getEntityId());
        if (orderId == null) {
            return OperationResult.fail("Order id is missing for resume.", false);
        }

        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElse(null);
        if (order == null) {
            return OperationResult.fail("Order " + orderId + " not found.", false);
        }

        boolean updated = false;
        for (OrderDeliveryTargetEntity target : order.getDeliveryTargets()) {
            if (!(target.getContactMethod() instanceof EmailContact existing)) {
                continue;
            }
            EmailContact replacement = new EmailContact(correctedEmail);
            replacement.setType(ContactMethodType.EMAIL);
            replacement.setLabel(existing.getLabel());
            replacement.setClient(existing.getClient());
            replacement.setUser(existing.getUser());
            target.setContactMethod(contactManagementService.saveContact(replacement));
            updated = true;
        }

        if (!updated) {
            EmailContact replacement = new EmailContact(correctedEmail);
            replacement.setType(ContactMethodType.EMAIL);
            replacement.setLabel("Order delivery e-mail");
            for (OrderDeliveryTargetEntity target : order.getDeliveryTargets()) {
                target.setContactMethod(contactManagementService.saveContact(replacement));
                updated = true;
                break;
            }
        }

        if (!updated) {
            return OperationResult.fail("No delivery target found for e-mail correction.", false);
        }

        orderRepository.save(order);
        return OperationResult.ok(null);
    }

    private Optional<Long> findDuplicateOrderId(OrderOperationContext orderCtx) {
        List<String> emails = resolveEmails(orderCtx);
        if (emails.isEmpty()) {
            log.debug("Skip duplicate check because order e-mail is empty");
            return Optional.empty();
        }
        String itemsJson = serializeItems(orderCtx);
        if (itemsJson == null) {
            return Optional.empty();
        }
        log.debug("Duplicate check input: emails={}, portalId={}, whiteAdminId={}, comment={}, itemsJson={}",
                emails,
                orderCtx.getPortalId(),
                orderCtx.getWhiteAdminId(),
                orderCtx.getUserComment(),
                itemsJson);

        Set<Long> candidateIds = new HashSet<>();
        if (orderCtx.getPortalId() != null) {
            orderRepository.findAllByPortalId(orderCtx.getPortalId()).stream()
                    .map(OrderEntity::getId)
                    .forEach(candidateIds::add);
        }
        if (orderCtx.getWhiteAdminId() != null) {
            orderRepository.findAllByWhiteAdminId(orderCtx.getWhiteAdminId()).stream()
                    .map(OrderEntity::getId)
                    .forEach(candidateIds::add);
        }
        if (!isBlank(orderCtx.getUserComment())) {
            orderRepository.findAllByUserCommentNormalized(orderCtx.getUserComment().trim()).stream()
                    .map(OrderEntity::getId)
                    .forEach(candidateIds::add);
        }

        if (candidateIds.isEmpty()
                && orderCtx.getPortalId() == null
                && orderCtx.getWhiteAdminId() == null
                && isBlank(orderCtx.getUserComment())) {
            // Fallback to e-mail candidates only when request has no explicit reference at all.
            // If whiteAdminId/portalId/comment is provided, using e-mail fallback causes false duplicates
            // across unrelated orders with same customer e-mail.
            for (String email : emails) {
                orderRepository.findOrderIdsByEmailDeliveryTarget(email.trim())
                        .forEach(candidateIds::add);
            }
            if (candidateIds.isEmpty()) {
                log.debug("No duplicate candidates by portal/whiteAdmin/comment/email");
                return Optional.empty();
            }
            log.debug("Duplicate candidates recovered by email fallback: {}", candidateIds.size());
        }
        if (candidateIds.isEmpty()) {
            log.debug("No duplicate candidates by explicit references");
            return Optional.empty();
        }

        return candidateIds.stream()
                .sorted(Comparator.naturalOrder())
                .filter(orderId -> {
                    OrderStatus status = orderRepository.findById(orderId)
                            .map(OrderEntity::getOrderStatus)
                            .orElse(null);
                    boolean eligible = isSuccessfulDuplicateCandidateStatus(status);
                    if (!eligible) {
                        log.debug("Duplicate candidate {} rejected: order status is {}", orderId, status);
                    }
                    return eligible;
                })
                .filter(orderId -> {
                    boolean sameEmail = emails.stream()
                            .anyMatch(email -> orderRepository.hasEmailDeliveryTarget(orderId, email.trim()));
                    if (!sameEmail) {
                        log.debug("Duplicate candidate {} rejected: email mismatch", orderId);
                    }
                    return sameEmail;
                })
                .filter(orderId -> {
                    boolean containsRequestedItems = orderRepository.containsOrderItems(orderId, itemsJson);
                    if (!containsRequestedItems) {
                        log.debug("Duplicate candidate {} rejected: does not contain requested item set", orderId);
                    } else {
                        log.debug("Duplicate candidate {} accepted: contains requested item set", orderId);
                    }
                    return containsRequestedItems;
                })
                .findFirst();
    }

    private boolean isSuccessfulDuplicateCandidateStatus(OrderStatus status) {
        return status == OrderStatus.SENT || status == OrderStatus.PROCESSED;
    }

    private String serializeItems(OrderOperationContext orderCtx) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (var spec : orderCtx.getItemSpecs()) {
            List<String> keyTypes = spec.keyTypes() == null
                    ? List.of()
                    : spec.keyTypes().stream().map(Enum::name).sorted().toList();
            BusinessPeriod period = spec.period();
            long periodDays = periodNormalizer.toDays(period);
            String periodUnit = period == null ? "DAY" : period.unit().name();
            int periodAmount = period == null ? 0 : period.amount();

            values.add(Map.of(
                    "pc_per_license", spec.computers(),
                    "lic_count", spec.count(),
                    "period_days", periodDays,
                    "period_amount", periodAmount,
                    "period_unit", periodUnit,
                    "key_types", keyTypes
            ));
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            log.warn("Failed to serialize order items for duplicate check: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private List<String> resolveEmails(OrderOperationContext orderCtx) {
        if (orderCtx == null) {
            return List.of();
        }
        if (orderCtx.getEmails() != null && !orderCtx.getEmails().isEmpty()) {
            return orderCtx.getEmails().stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .toList();
        }
        if (!isBlank(orderCtx.getEmail())) {
            return List.of(orderCtx.getEmail().trim());
        }
        return List.of();
    }

    private String extractFirst(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            return "";
        }
        return values.getFirst().trim();
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long resolveQuestionReferenceId(OrderOperationContext orderCtx) {
        if (orderCtx.getPortalId() != null) {
            return orderCtx.getPortalId();
        }
        return orderCtx.getWhiteAdminId();
    }

    private String resolveQuestionCurrentReference(OrderOperationContext orderCtx) {
        if (orderCtx.getPortalId() != null) {
            return "portalId=" + orderCtx.getPortalId();
        }
        if (orderCtx.getWhiteAdminId() != null) {
            return "whiteAdminId=" + orderCtx.getWhiteAdminId();
        }
        if (!isBlank(orderCtx.getUserComment())) {
            return "comment=" + orderCtx.getUserComment().trim();
        }
        return null;
    }
}
