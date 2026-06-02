package com.zillya.timonfech.zillwrapper.core.source;

import com.zillya.timonfech.zillwrapper.core.entities.security.Identity;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.security.AuthenticationHandler;
import com.zillya.timonfech.zillwrapper.core.security.IdentityService;
import com.zillya.timonfech.zillwrapper.core.security.UserAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramAuthenticationHandler implements AuthenticationHandler<TelegramInboundEvent> {

    private final IdentityService identityService;
    private final UserAuthenticationService authService;

    @Override
    public UserEntity authenticate(TelegramInboundEvent event) {
        // 1. Resolve Identity Factors (TELEGRAM_ID, TELEGRAM_NICKNAME)
        Identity identity = identityService.resolveIdentity(event);
        
        // 2. Perform Strict Matching against user_sources table
        return authService.authenticate(identity);
    }

    @Override
    public boolean supports(InboundEvent<?> event) {
        return event instanceof TelegramInboundEvent;
    }
}
