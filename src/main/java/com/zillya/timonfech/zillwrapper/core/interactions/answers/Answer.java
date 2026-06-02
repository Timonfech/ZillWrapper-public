package com.zillya.timonfech.zillwrapper.core.interactions.answers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = YesNoAnswer.class, name = "YES_NO_ANS"),
        @JsonSubTypes.Type(value = StringsListAnswer.class, name = "STRINGS_LIST_ANS")
})
public sealed interface Answer
        permits YesNoAnswer, StringsListAnswer {
}