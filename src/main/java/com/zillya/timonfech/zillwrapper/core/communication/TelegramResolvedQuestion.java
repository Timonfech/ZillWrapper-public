package com.zillya.timonfech.zillwrapper.core.communication;

import java.math.BigInteger;

public record TelegramResolvedQuestion(
        BigInteger operationId,
        TelegramQuestionQueueItem question
) {
}
