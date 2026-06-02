package com.zillya.timonfech.zillwrapper.core.exceptions;

import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;

public class NeedUserInteractionException extends RuntimeException {
    private final Question question;

    public NeedUserInteractionException(Question question) {
        this.question = question;
    }

    public Question getQuestion() {
        return question;
    }
}