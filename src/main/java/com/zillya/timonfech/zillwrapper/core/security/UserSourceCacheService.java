package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.security.Identity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
public class UserSourceCacheService {

    public static final String AUTH_CACHE = "user_auth_cache";

    /**
     * Attempts to get an authenticated user from the cache.
     * If not found, calls the provider and caches the result.
     * 
     * @param identity The identity to authenticate
     * @param authenticator The logic to run if cache miss
     * @return Authenticated UserEntity
     */
    @Cacheable(value = AUTH_CACHE, key = "#identity")
    public UserEntity getAuthenticatedUser(Identity identity, Supplier<UserEntity> authenticator) {
        log.debug("Cache miss for identity: {}. Performing full authentication.", identity);
        return authenticator.get();
    }

    /**
     * Clears the authentication cache.
     */
    @CacheEvict(value = AUTH_CACHE, allEntries = true)
    public void clearCache() {
        log.info("Authentication cache cleared.");
    }
}
