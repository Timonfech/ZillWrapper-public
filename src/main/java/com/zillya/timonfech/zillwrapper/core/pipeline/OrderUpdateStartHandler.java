package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderUpdateStartHandler implements OperationHandler<IOperationContext> {

    private final OrderRepository orderRepository;

    @Override
    public String name() {
        return "ORDER_UPDATE";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.ORDER_UPDATE;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.ORDER_UPDATE
                && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.ORDER_UPDATE, stepProps = {OperationStep.Props.START})
    @Override
    public OperationResult<?> handle(IOperationContext context) {
        OrderOperationContext orderCtx = (OrderOperationContext) context;
        Long orderId = orderCtx.getOrderId();
        if (orderId == null) {
            return OperationResult.fail("ORDER_UPDATE requires orderId", false);
        }
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return OperationResult.fail("Order not found: " + orderId, false);
        }
        return OperationResult.ok(null);
    }
}
