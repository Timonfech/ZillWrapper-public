package com.zillya.timonfech.zillwrapper.apis.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncOrchestrator {
    private final List<EntitySyncHandler> handlers;

    public void executeSync(SyncRequest request) {
        EntitySyncHandler handler = handlers.stream()
                .filter(h -> h.supports(request))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No sync handler found for request: " + request));

        log.info("Executing sync with handler: {}", handler.getClass().getSimpleName());
        handler.sync(request);
    }
}
