package com.zillya.timonfech.zillwrapper.core.services;

import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceManagementService {

    private final SourceRepository sourceRepository;

    // In-memory cache for all sources
    private final Map<String, SourceEntity> sourceCache = new ConcurrentHashMap<>();

    /**
     * Initializes the cache by loading all sources from the database.
     */
    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * Loads all sources into memory.
     */
    public synchronized void refreshCache() {
        log.info("Loading all sources into memory cache...");
        sourceRepository.findAll().forEach(source -> {
            String key = getCacheKey(source.getType(), source.getIdentifierName());
            sourceCache.put(key, source);
        });
        log.info("Loaded {} sources into memory.", sourceCache.size());
    }

    /**
     * Gets a source by type and identifier. Returns from cache.
     */
    public Optional<SourceEntity> getSource(SourceType type, String identifierName) {
        String key = getCacheKey(type, identifierName);
        SourceEntity source = sourceCache.get(key);
        
        if (source == null) {
            // Fallback to DB if not found in cache (rare case)
            return sourceRepository.findByTypeAndIdentifierName(type, identifierName)
                    .map(s -> {
                        sourceCache.put(key, s);
                        return s;
                    });
        }
        
        return Optional.of(source);
    }

    /**
     * Gets a source by type and identifier. Creates and saves if not found.
     */
    public synchronized SourceEntity getOrCreateSource(SourceType type, String identifierName) {
        return getSource(type, identifierName)
                .orElseGet(() -> {
                    SourceEntity newSource = sourceRepository.save(new SourceEntity(null, type, identifierName));
                    String key = getCacheKey(type, identifierName);
                    sourceCache.put(key, newSource);
                    return newSource;
                });
    }

    /**
     * Generates a cache key for the source.
     */
    private String getCacheKey(SourceType type, String identifierName) {
        return type.name() + ":" + (identifierName != null ? identifierName : "");
    }
}
