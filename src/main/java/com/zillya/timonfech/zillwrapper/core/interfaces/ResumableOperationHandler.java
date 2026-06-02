package com.zillya.timonfech.zillwrapper.core.interfaces;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.Answer;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;

public interface ResumableOperationHandler<Q extends Question<A>, A extends Answer> {
    boolean supports(OperationExecutionEntity stageExecution, Q question, A answer);
    OperationResult<?> resume(OperationExecutionEntity stageExecution, Q question, A answer);
}
