package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;

import java.math.BigInteger;

public record ResolvedResendTarget(
        EntityTypeEnum entityType,
        Long entityId,
        BigInteger rootOperationId,
        String source
) {
}
