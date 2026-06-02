package com.zillya.timonfech.zillwrapper.core.pipeline.plan;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CoreOperationPlanBuilder implements OperationPlanBuilder {

    @Override
    public boolean supports(OperationType rootType, OrderOperationContext context) {
        return rootType != null;
    }

    @Override
    public ExecutionPlan build(OperationType rootType, OrderOperationContext context) {
        List<OperationType> chain = switch (resolveEffectiveRoot(rootType, context)) {
            case LICENSE_FULFILLMENT -> buildLicenseFulfillment(context != null && context.isIncludeLegacySync());
            case ORDER_UPDATE -> buildOrderUpdate(context != null && context.isIncludeLegacySync());
            case RESEND_NOTIFICATION -> List.of(
                    OperationType.RESEND_NOTIFICATION,
                    OperationType.ARTIFACT_GENERATION,
                    OperationType.NOTIFY
            );
            default -> List.of(rootType);
        };

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            OperationType stageType = chain.get(i);
            boolean cancelable = !isCrucial(stageType);
            boolean nonBlocking = stageType == OperationType.LEGACY_SYNC;
            steps.add(new PlanStep(stageType, i, cancelable, nonBlocking));
        }
        return new ExecutionPlan(rootType, steps);
    }

    private OperationType resolveEffectiveRoot(OperationType rootType, OrderOperationContext context) {
        if (rootType == OperationType.LICENSE_FULFILLMENT
                && context != null
                && context.getCurrentStage() == OperationType.ORDER_UPDATE) {
            return OperationType.ORDER_UPDATE;
        }
        return rootType;
    }

    private List<OperationType> buildLicenseFulfillment(boolean includeLegacySync) {
        List<OperationType> chain = new ArrayList<>(List.of(
                OperationType.ORDER_CREATION,
                OperationType.LICENSE_GENERATION
        ));
        if (includeLegacySync) {
            chain.add(OperationType.LEGACY_SYNC);
        }
        chain.add(OperationType.LICENSE_SUBSCRIPTION);
        chain.add(OperationType.ARTIFACT_GENERATION);
        chain.add(OperationType.NOTIFY);
        return List.copyOf(chain);
    }

    private List<OperationType> buildOrderUpdate(boolean includeLegacySync) {
        List<OperationType> chain = new ArrayList<>(List.of(
                OperationType.ORDER_UPDATE,
                OperationType.LICENSE_GENERATION
        ));
        if (includeLegacySync) {
            chain.add(OperationType.LEGACY_SYNC);
        }
        chain.add(OperationType.NOTIFY);
        return List.copyOf(chain);
    }

    private boolean isCrucial(OperationType stageType) {
        return stageType == OperationType.ORDER_CREATION
                || stageType == OperationType.LICENSE_GENERATION
                || stageType == OperationType.ARTIFACT_GENERATION;
    }
}

