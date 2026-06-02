package com.zillya.timonfech.zillwrapper.core.entities.source;

import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;

public interface SourceInteractionGateway {
    SourceType supports();
    void renderHandlingInfo(OperationExecutionEntity execution);
    void renderQuestion(OperationExecutionEntity execution, Question question);
    void renderCancel(OperationExecutionEntity execution);
    void renderResume(OperationExecutionEntity execution);
}