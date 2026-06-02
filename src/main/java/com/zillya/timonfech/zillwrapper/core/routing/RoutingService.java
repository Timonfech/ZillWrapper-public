package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.events.InboundErrorCategory;
import com.zillya.timonfech.zillwrapper.core.exceptions.RoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final List<IntentRouter<?>> routers;

    @SuppressWarnings("unchecked")
    public RoutingDecision route(InboundEvent<?> event) {
        log.info("RoutingService start eventId={} eventType={} routersCount={}",
                event.getId(),
                event.getClass().getSimpleName(),
                routers.size());
        IntentRouter<InboundEvent<?>> router = null;
        for (IntentRouter<?> rawRouter : routers) {
            IntentRouter<InboundEvent<?>> typed = (IntentRouter<InboundEvent<?>>) rawRouter;
            boolean canRoute = false;
            try {
                canRoute = typed.canRoute(event);
            } catch (Exception ex) {
                log.warn("RoutingService router threw in canRoute: router={} eventId={} error={}",
                        rawRouter.getClass().getSimpleName(),
                        event.getId(),
                        ex.getMessage());
            }
            log.info("RoutingService canRoute router={} eventId={} result={}",
                    rawRouter.getClass().getSimpleName(),
                    event.getId(),
                    canRoute);
            if (canRoute) {
                router = typed;
                break;
            }
        }
        if (router == null) {
            log.warn("RoutingService no router matched eventId={}", event.getId());
            return new RoutingDecision.IgnoreDecision("No intent router matched event");
        }
        try {
            log.info("RoutingService route with router={} eventId={}",
                    router.getClass().getSimpleName(),
                    event.getId());
            return router.route(event);
        } catch (RoutingException ex) {
            log.warn("RoutingService routing error router={} eventId={} message={}",
                    router.getClass().getSimpleName(),
                    event.getId(),
                    ex.getMessage());
            return new RoutingDecision.RoutingErrorDecision(
                    InboundErrorCategory.ROUTING,
                    ex.getMessage(),
                    ex.getMessage(),
                    ex
            );
        }
    }
}
