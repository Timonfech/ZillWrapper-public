package com.zillya.timonfech.zillwrapper.core.communication;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class TelegramQuestionQueueItem {
    private String questionId;
    private BigInteger parentOperationId;
    private BigInteger stageExecutionId;
    private String questionType;
    private String questionPayloadJson;
    private Integer questionMessageId;
    private TelegramQuestionStatus status;
    private Long createdAtEpochMs;
    private Long answeredAtEpochMs;
    private String answerPayloadJson;
}
