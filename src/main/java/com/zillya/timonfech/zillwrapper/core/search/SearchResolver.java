package com.zillya.timonfech.zillwrapper.core.search;

import java.util.List;

public interface SearchResolver<T> {
    SearchEntityType entityType();
    Class<T> modelType();
    List<T> resolve(SearchQuery query);
}

