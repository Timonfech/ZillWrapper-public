package com.zillya.timonfech.zillwrapper.core.interactions.questions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.Answer;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = YesNoQuestion.class, name = "YES_NO"),
        @JsonSubTypes.Type(value = NewStringsQuestion.class, name = "NEW_STRINGS"),
        @JsonSubTypes.Type(value = DuplicateQuestion.class, name = "DUPLICATE")
})

public sealed interface Question<A extends Answer>
        permits YesNoQuestion, NewStringsQuestion, DuplicateQuestion {
}
