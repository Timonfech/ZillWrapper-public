package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;
@Getter
public class QuestionRequiredEvent extends ApplicationEvent {
    private final Question<?> question;
    private final BigInteger parentOperationId;
    private final BigInteger stageExecutionId;
    private final boolean parentBlocking;

    public QuestionRequiredEvent(Object source,
                                 BigInteger parentOperationId,
                                 BigInteger stageExecutionId,
                                 boolean parentBlocking,
                                 Question<?> question) {
        super(source);
        this.parentOperationId = parentOperationId;
        this.stageExecutionId = stageExecutionId;
        this.parentBlocking = parentBlocking;
        this.question = question;
    }

    // Backward-compatible alias for older listeners.
    public BigInteger getOperationId() {
        return parentOperationId;
    }
}
