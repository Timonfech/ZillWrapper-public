package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEventStatus;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.events.InboundErrorCategory;
import com.zillya.timonfech.zillwrapper.core.exceptions.AuthenticationException;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.routing.RoutingDecision;
import com.zillya.timonfech.zillwrapper.core.routing.RoutingService;
import com.zillya.timonfech.zillwrapper.core.security.AuthenticationHandler;
import com.zillya.timonfech.zillwrapper.core.security.OrderSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboundEventOrchestrator {

    private final List<AuthenticationHandler<?>> authHandlers;
    private final RoutingService routingService;
    private final OrderSecurityService securityService;
    private final RoutingDecisionExecutor routingDecisionExecutor;

    @EventListener
    public void onInboundEvent(InboundEvent<?> event) {
        log.info("Inbound orchestrator received eventId={} sourceId={} eventType={}",
                event.getId(),
                event.getSourceEntity() != null ? event.getSourceEntity().getId() : null,
                event.getClass().getSimpleName());
        try {
            UserEntity user = authHandlers.stream()
                    .filter(h -> h.supports(event))
                    .findFirst()
                    .map(h -> ((AuthenticationHandler<InboundEvent<?>>) h).authenticate(event))
                    .orElseThrow(() -> new IllegalStateException("Unsupported transport for event: " + event.getClass().getSimpleName()));
            log.info("Inbound auth success eventId={} userId={}", event.getId(), user != null ? user.getId() : null);

            event.setStatus(InboundEventStatus.AUTHORIZED);
            securityService.checkGeneralAccess(user);
            log.info("Inbound access check passed eventId={}", event.getId());

            RoutingDecision decision = routingService.route(event);
            log.info("Inbound routing decision eventId={} decision={}",
                    event.getId(),
                    decision != null ? decision.getClass().getSimpleName() : null);
            RoutingDecisionExecutor.DecisionExecutionResult result = routingDecisionExecutor.execute(event, user, decision);
            log.info("Inbound decision execution result eventId={} result={}", event.getId(), result);
            switch (result) {
                case ROUTED -> event.setStatus(InboundEventStatus.ROUTED);
                case IGNORED -> event.setStatus(InboundEventStatus.IGNORED);
                case ERROR -> event.setStatus(InboundEventStatus.ERROR);
            }
        } catch (NeedUserInteractionException ex) {
            log.info("Operation on event {} waits for user interaction", event.getId());
        } catch (OperationCancelledException ex) {
            log.info("Operation on event {} was cancelled", event.getId());
        } catch (AuthenticationException ex) {
            log.warn("Inbound authentication failed eventId={} reason={} sourceId={}: {}",
                    event.getId(),
                    ex.getReason(),
                    ex.getSourceId(),
                    ex.getMessage());
            event.setStatus(InboundEventStatus.ERROR);
            routingDecisionExecutor.execute(
                    event,
                    null,
                    new RoutingDecision.RoutingErrorDecision(
                            InboundErrorCategory.VALIDATION,
                            "You are not authorized for this bot.",
                            ex.getMessage(),
                            ex
                    )
            );
        } catch (SecurityException ex) {
            log.warn("Inbound access denied eventId={}: {}", event.getId(), ex.getMessage());
            event.setStatus(InboundEventStatus.ERROR);
            routingDecisionExecutor.execute(
                    event,
                    null,
                    new RoutingDecision.RoutingErrorDecision(
                            InboundErrorCategory.VALIDATION,
                            "Access denied.",
                            ex.getMessage(),
                            ex
                    )
            );
        } catch (Exception ex) {
            log.error("Orchestration failed for event {}: {}", event.getId(), ex.getMessage(), ex);
            event.setStatus(InboundEventStatus.ERROR);
            routingDecisionExecutor.execute(
                    event,
                    null,
                    new RoutingDecision.RoutingErrorDecision(
                            InboundErrorCategory.SYSTEM,
                            "Internal error. Please try again later.",
                            ex.getMessage(),
                            ex
                    )
            );
        }
    }
}
