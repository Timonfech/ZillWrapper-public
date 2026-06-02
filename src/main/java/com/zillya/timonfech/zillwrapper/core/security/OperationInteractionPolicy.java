package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationInteractionPolicy {

    public static final String INTERACTION_CACHE = "interactionPolicyCache";

    private final SourceRepository sourceRepository;

    public boolean isInteractive(InboundEvent<?> sourceContext, Long sourceId, OperationType operationType) {
        if (sourceContext instanceof TelegramInboundEvent) {
            return true;
        }
        if (sourceId == null) {
            return false;
        }
        return isInteractiveBySourceId(sourceId);
    }

    @Cacheable(value = INTERACTION_CACHE, key = "#sourceId")
    public boolean isInteractiveBySourceId(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .map(source -> source.getType() == SourceType.TELEGRAM)
                .orElse(false);
    }
}
