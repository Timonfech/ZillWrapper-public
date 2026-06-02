package com.zillya.timonfech.zillwrapper.core.interactions.commands;

public interface SourceCommandAdapter<T> {
    CommandIntent toIntent(T inbound, Long actorUserId, Long sourceId, String payload, com.zillya.timonfech.zillwrapper.core.entities.OperationType operationType);
    InteractionAction toAction(String callbackAction);
}

