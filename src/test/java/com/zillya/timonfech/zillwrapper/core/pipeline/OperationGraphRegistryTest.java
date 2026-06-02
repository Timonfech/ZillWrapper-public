package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationGraphRegistryTest {

    private final OperationGraphRegistry registry = new OperationGraphRegistry(
            java.util.List.of(new com.zillya.timonfech.zillwrapper.core.pipeline.plan.CoreOperationPlanBuilder())
    );

    @Test
    void shouldBuildCreateFlowForLicenseFulfillmentByDefault() {
        OrderOperationContext ctx = new OrderOperationContext(1L, null, "", List.of(), List.of(), null);
        ctx.setIncludeLegacySync(true);
        List<OperationType> plan = registry.stagesForExecution(OperationType.LICENSE_FULFILLMENT, ctx);
        assertEquals(List.of(
                OperationType.ORDER_CREATION,
                OperationType.LICENSE_GENERATION,
                OperationType.LEGACY_SYNC,
                OperationType.LICENSE_SUBSCRIPTION,
                OperationType.ARTIFACT_GENERATION,
                OperationType.NOTIFY
        ), plan);
    }

    @Test
    void shouldBuildUpdateFlowWhenCurrentStageIsOrderUpdate() {
        OrderOperationContext ctx = new OrderOperationContext(1L, null, "", List.of(), List.of(), null);
        ctx.setCurrentStage(OperationType.ORDER_UPDATE);
        ctx.setIncludeLegacySync(true);
        List<OperationType> plan = registry.stagesForExecution(OperationType.LICENSE_FULFILLMENT, ctx);
        assertEquals(List.of(
                OperationType.ORDER_UPDATE,
                OperationType.LICENSE_GENERATION,
                OperationType.LEGACY_SYNC,
                OperationType.NOTIFY
        ), plan);
    }

    @Test
    void shouldExcludeLegacySyncWhenDisabled() {
        OrderOperationContext ctx = new OrderOperationContext(1L, null, "", List.of(), List.of(), null);
        ctx.setCurrentStage(OperationType.ORDER_UPDATE);
        ctx.setIncludeLegacySync(false);
        List<OperationType> plan = registry.stagesForExecution(OperationType.LICENSE_FULFILLMENT, ctx);
        assertEquals(List.of(
                OperationType.ORDER_UPDATE,
                OperationType.LICENSE_GENERATION,
                OperationType.NOTIFY
        ), plan);
    }
}
