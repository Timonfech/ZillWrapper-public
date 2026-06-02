package com.zillya.timonfech.zillwrapper.core.communication;

public record ResolvedEmailTemplate(
        String ruleId,
        String template,
        String subjectSingleKey,
        String subjectPluralKey,
        String subjectOfflineSuffixKey
) {
}
