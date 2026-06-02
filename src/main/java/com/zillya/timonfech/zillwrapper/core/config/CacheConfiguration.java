package com.zillya.timonfech.zillwrapper.core.config;

import com.zillya.timonfech.zillwrapper.core.security.OperationInteractionPolicy;
import com.zillya.timonfech.zillwrapper.core.security.UserSourceCacheService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                UserSourceCacheService.AUTH_CACHE,
                OperationInteractionPolicy.INTERACTION_CACHE
        );
    }
}
