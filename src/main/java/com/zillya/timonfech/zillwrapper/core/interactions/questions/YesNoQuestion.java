package com.zillya.timonfech.zillwrapper.core.interactions.questions;

import com.zillya.timonfech.zillwrapper.core.interactions.answers.YesNoAnswer;

public record YesNoQuestion(String message)
        implements Question<YesNoAnswer> {
}