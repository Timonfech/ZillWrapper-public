package com.zillya.timonfech.zillwrapper.core.interactions.questions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.YesNoAnswer;


@JsonIgnoreProperties(ignoreUnknown = true)
public record DuplicateQuestion(
        String entityType,
        Long entityId,
        String currentReference,
        String duplicateEntityType,
        Long duplicateEntityId
) implements Question<YesNoAnswer> {

    public DuplicateQuestion(
            String entityType,
            Long entityId,
            String duplicateEntityType,
            Long duplicateEntityId
    ) {
        this(entityType, entityId, null, duplicateEntityType, duplicateEntityId);
    }
}
