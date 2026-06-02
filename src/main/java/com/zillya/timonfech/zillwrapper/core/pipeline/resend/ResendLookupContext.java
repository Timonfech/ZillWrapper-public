package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;

public record ResendLookupContext(
        EntityTypeEnum entityType,
        Long explicitOrderId,
        Long portalId,
        Long whiteAdminId,
        String referenceId,
        String userComment,
        Long chatId,
        Integer controlMessageId,
        Long initiatorUserId
) {
}
