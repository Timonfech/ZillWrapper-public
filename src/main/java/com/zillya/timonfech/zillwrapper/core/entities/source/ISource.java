package com.zillya.timonfech.zillwrapper.core.entities.source;

import com.zillya.timonfech.zillwrapper.core.IEntityType;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;

public interface ISource extends IEntityType {
    Long getSourceId();
    SourceType getSourceType();
    String identifierName();
}
