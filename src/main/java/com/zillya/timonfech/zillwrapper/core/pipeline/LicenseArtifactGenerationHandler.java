package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.zillya.timonfech.zillwrapper.core.aspects.OperationStep.Props.CRUCIAL;
import static com.zillya.timonfech.zillwrapper.core.aspects.OperationStep.Props.INTERACTIVE;

/**
 * Artifact stage handler dedicated to license-based artifacts.
 * Uses pluggable generators to keep stage extensible for future artifact domains.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseArtifactGenerationHandler implements OperationHandler<IOperationContext> {

    private final List<OrderArtifactGenerator> artifactGenerators;

    @Override
    public String name() {
        return "ARTIFACT_GENERATION";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.ARTIFACT_GENERATION;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.ARTIFACT_GENERATION
                && context instanceof OrderOperationContext;
    }

    @Override
    @OperationStep(type = OperationType.ARTIFACT_GENERATION, stepProps = {INTERACTIVE, CRUCIAL})
    public OperationResult<?> handle(IOperationContext context) {
        OrderOperationContext orderCtx = asOrderContext(context).orElse(null);
        if (orderCtx == null) {
            return OperationResult.fail("ARTIFACT_GENERATION requires OrderOperationContext", false);
        }
        if (orderCtx.getOrderId() == null) {
            return OperationResult.fail("Cannot generate artifacts without orderId", false);
        }

        for (OrderArtifactGenerator generator : artifactGenerators) {
            if (!generator.supports(orderCtx)) {
                continue;
            }
            OperationResult<?> result = generator.generate(orderCtx);
            if (result != null && !result.isSuccess()) {
                return result;
            }
        }

        log.info("Artifact generation completed for order {}", orderCtx.getOrderId());
        return OperationResult.ok(null);
    }
}
