package com.zillya.timonfech.zillwrapper.core;

public interface IEntityWithStatus <S extends Enum<S>> extends IEntityType{
    S getStatus();
}
