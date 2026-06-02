package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseGenerationHandler implements OperationHandler<IOperationContext> {

    private final OrderItemRepository orderItemRepository;
    private final List<LicenseGenerator> generators;
    private final ProductRegistry productRegistry;

    @Override
    public String name() {
        return "LICENSE_GENERATION";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.LICENSE_GENERATION;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.LICENSE_GENERATION && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.LICENSE_GENERATION, stepProps = {OperationStep.Props.CRUCIAL, OperationStep.Props.INTERACTIVE})
    @Override
    public OperationResult<?> handle(IOperationContext context) throws OperationCancelledException {
        OrderOperationContext orderCtx = asOrderContext(context).orElse(null);
        if (orderCtx == null) {
            return OperationResult.fail("Handler requires OrderOperationContext", false);
        }

        log.info("Starting License Generation for order: {}", orderCtx.getOrderId());
        
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderCtx.getOrderId());

        List<OrderItemEntity> pendingItems = items.stream()
                .filter(item -> item.getProcessingStatus() == ItemProcessingStatus.PENDING)
                .filter(item -> item.getId() != null)
                .toList();
        if (pendingItems.isEmpty()) {
            return OperationResult.fail("No pending items for license generation in order " + orderCtx.getOrderId(), false);
        }
        List<Long> pendingItemIds = pendingItems.stream().map(OrderItemEntity::getId).toList();

        log.info("Starting LICENSE_GENERATION parentOpId={} stageExecId={} orderId={} pendingItems={}",
                orderCtx.getOperationId(),
                orderCtx.getStageExecutionId(),
                orderCtx.getOrderId(),
                pendingItemIds.size());

        for (OrderItemEntity pendingItem : pendingItems) {
            processSingle(orderCtx, pendingItem);
        }

        int total = pendingItemIds.size();
        int generated = (int) orderItemRepository.countByIdInAndProcessingStatusIn(
                pendingItemIds,
                List.of(
                        ItemProcessingStatus.GENERATED
//                        ItemProcessingStatus.ARTIFACTS_READY,
//                        ItemProcessingStatus.DELIVERED
                )
        );
        int failed = (int) orderItemRepository.countByIdInAndProcessingStatusIn(
                pendingItemIds,
                List.of(ItemProcessingStatus.FAILED)
        );
        log.info("LICENSE_GENERATION aggregate result orderId={} total={} generated={} failed={}",
                orderCtx.getOrderId(), total, generated, failed);

        String summary = "License generation: " + generated + "/" + total + " generated, failed=" + failed;
        if (generated == 0) {
            return OperationResult.fail(summary, false);
        }

        return OperationResult.ok(null);
    }

    protected void processSingle(OrderOperationContext orderCtx, OrderItemEntity item) {
        if (item == null || item.getId() == null) {
            log.warn("License generation skip: item snapshot is null or has null id");
            return;
        }
        Long orderItemId = item.getId();
        if (item.getProcessingStatus() != ItemProcessingStatus.PENDING) {
            log.info("License generation skip: item {} snapshot has non-pending status {}", orderItemId, item.getProcessingStatus());
            return;
        }
        try {
            ProductInfo product = productRegistry.getProductById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            Optional<SourceType> sourceType = productRegistry.resolveSourceType(product.productId(), product.brandId());
            if (sourceType.isEmpty()) {
                log.warn("License generation failed: source type not resolved for item {} product={}/{}",
                        orderItemId, product.brandId(), product.productId());
                orderItemRepository.updateProcessingStatusById(item.getId(), ItemProcessingStatus.FAILED);
                return;
            }
            List<LicenseGenerator> matched = generators.stream()
                    .filter(g -> g.sourceType().isPresent() && g.sourceType().get() == sourceType.get())
                    .filter(g -> g.supports(product))
                    .toList();
            if (matched.size() != 1) {
                log.warn("License generation failed: expected exactly one generator, found {} for item {} source={} product={}/{}",
                        matched.size(), orderItemId, sourceType.get(), product.brandId(), product.productId());
                orderItemRepository.updateProcessingStatusById(item.getId(), ItemProcessingStatus.FAILED);
                return;
            }
            matched.getFirst().generate(item, product, orderCtx.getSourceId());
            OrderItemEntity after = orderItemRepository.findById(orderItemId).orElse(null);
            if (after != null && after.getProcessingStatus() == ItemProcessingStatus.PENDING) {
                log.warn("License generation anomaly: item {} remained PENDING after successful generator call. Forcing GENERATED.", orderItemId);
                orderItemRepository.updateProcessingStatusById(orderItemId, ItemProcessingStatus.GENERATED);
            }
        } catch (Exception ex) {
            log.error("License generation failed for item {}: {}", orderItemId, ex.getMessage(), ex);
            orderItemRepository.updateProcessingStatusById(orderItemId, ItemProcessingStatus.FAILED);
        }
    }
}
