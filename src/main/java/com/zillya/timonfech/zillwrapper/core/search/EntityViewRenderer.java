package com.zillya.timonfech.zillwrapper.core.search;

public interface EntityViewRenderer<T> {
    SearchEntityType entityType();
    Class<T> modelType();
    String render(T entity);
    Long internalId(T entity);
}

