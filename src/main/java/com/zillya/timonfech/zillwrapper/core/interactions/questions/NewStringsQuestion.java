package com.zillya.timonfech.zillwrapper.core.interactions.questions;

import com.zillya.timonfech.zillwrapper.core.interactions.answers.StringsListAnswer;

import java.util.Map;

public record NewStringsQuestion(
        Map<String, String> dataKeyValue
) implements Question<StringsListAnswer> {

}
