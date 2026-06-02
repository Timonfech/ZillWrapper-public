package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.security.Identity;
import com.zillya.timonfech.zillwrapper.core.entities.security.IdentityExtractor;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final List<IdentityExtractor<?>> extractors;

    /**
     * Resolves an Identity from an inbound event.
     * Does NOT perform authentication/mapping to a UserEntity.
     */
    @SuppressWarnings("unchecked")
    public Identity resolveIdentity(InboundEvent<?> event) {
        SourceType type = event.getSourceEntity().getType();

        return extractors.stream()
                .filter(e -> e.sourceType() == type)
                .findFirst()
                .map(e -> ((IdentityExtractor<InboundEvent<?>>) e).extract(event))
                .orElseThrow(() -> new IllegalStateException("No IdentityExtractor found for type: " + type));
    }
}
