package com.zillya.timonfech.zillwrapper.core.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UnifiedSearchService {
    private final List<SearchResolver<?>> resolvers;
    private final List<EntityViewRenderer<?>> renderers;
    private final ReplyCorrelationResolver replyCorrelationResolver;

    public Optional<Long> resolveOrderIdByReplyCorrelation(Long chatId, Integer replyToMessageId) {
        return replyCorrelationResolver.resolveOrderIdByReplyCorrelation(chatId, replyToMessageId);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> resolve(SearchEntityType entityType, SearchQuery query, Class<T> modelType) {
        SearchResolver<T> resolver = (SearchResolver<T>) resolvers.stream()
                .filter(r -> r.entityType() == entityType && r.modelType().equals(modelType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No resolver for " + entityType + "/" + modelType.getSimpleName()));
        return resolver.resolve(query);
    }

    @SuppressWarnings("unchecked")
    public <T> String render(SearchEntityType entityType, T entity, Class<T> modelType) {
        EntityViewRenderer<T> renderer = (EntityViewRenderer<T>) renderers.stream()
                .filter(r -> r.entityType() == entityType && r.modelType().equals(modelType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No renderer for " + entityType + "/" + modelType.getSimpleName()));
        return renderer.render(entity);
    }

    @SuppressWarnings("unchecked")
    public <T> Long internalId(SearchEntityType entityType, T entity, Class<T> modelType) {
        EntityViewRenderer<T> renderer = (EntityViewRenderer<T>) renderers.stream()
                .filter(r -> r.entityType() == entityType && r.modelType().equals(modelType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No renderer for " + entityType + "/" + modelType.getSimpleName()));
        return renderer.internalId(entity);
    }
}

