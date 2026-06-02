package com.zillya.timonfech.zillwrapper.core.interactions.commands;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.search.SearchEntityType;
import lombok.Builder;

@Builder
public record CommandIntent(
        Long sourceId,
        OperationType operationType,
        SearchEntityType entityType,
        String payload,
        Long chatId,
        Integer messageThreadId,
        Integer messageId,
        Integer replyToMessageId,
        Long actorUserId
) {
}
