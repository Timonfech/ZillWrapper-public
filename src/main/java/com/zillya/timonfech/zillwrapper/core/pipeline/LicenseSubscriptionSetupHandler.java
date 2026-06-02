package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.subscription.LicenseSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseSubscriptionSetupHandler implements OperationHandler<IOperationContext> {

    private final LicenseSubscriptionService subscriptionService;

    @Override
    public String name() {
        return "LICENSE_SUBSCRIPTION";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.LICENSE_SUBSCRIPTION;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.LICENSE_SUBSCRIPTION
                && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.LICENSE_SUBSCRIPTION, stepProps = {
            OperationStep.Props.INTERACTIVE
    })
    @Override
    public OperationResult<?> handle(IOperationContext context) {
        OrderOperationContext orderCtx = (OrderOperationContext) context;
        if (orderCtx.getOrderId() == null) {
            return OperationResult.ok(null);
        }
        var summary = subscriptionService.setupSubscriptionsForOrder(
                orderCtx.getOrderId(),
                orderCtx.getEntitySourceId(),
                orderCtx.getInitiatorUserId(),
                orderCtx.getItemSpecs()
        );
        if (summary.failed() > 0) {
            String warning = "Subscription setup warnings: failed=" + summary.failed();
            orderCtx.addWarning(warning);
            log.warn("{} for order {}. details={}", warning, orderCtx.getOrderId(), summary.errors());
        }
        return OperationResult.ok(null);
    }
}
