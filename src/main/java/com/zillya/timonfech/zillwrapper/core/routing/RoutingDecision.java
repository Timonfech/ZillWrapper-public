package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.events.InboundErrorCategory;
import com.zillya.timonfech.zillwrapper.core.interactions.commands.CommandIntent;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;

public sealed interface RoutingDecision permits RoutingDecision.StartPipelineDecision,
        RoutingDecision.InteractionDecision,
        RoutingDecision.MenuDecision,
        RoutingDecision.SearchDecision,
        RoutingDecision.ControlCallbackDecision,
        RoutingDecision.PreviewDecision,
        RoutingDecision.IgnoreDecision,
        RoutingDecision.RoutingErrorDecision {

    record StartPipelineDecision(IOperationContext context) implements RoutingDecision {}

    record InteractionDecision(TelegramInboundEvent event) implements RoutingDecision {}

    record MenuDecision(TelegramInboundEvent event) implements RoutingDecision {}

    record SearchDecision(CommandIntent intent) implements RoutingDecision {}

    record ControlCallbackDecision(TelegramInboundEvent event) implements RoutingDecision {}

    record PreviewDecision(TelegramInboundEvent event, OrderOperationContext context) implements RoutingDecision {}

    record IgnoreDecision(String reason) implements RoutingDecision {}

    record RoutingErrorDecision(InboundErrorCategory category,
                                String safeMessage,
                                String internalMessage,
                                Throwable cause) implements RoutingDecision {}
}
