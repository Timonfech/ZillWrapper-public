package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;

public interface ResendTargetResolver {
    boolean supports(EntityTypeEnum entityType);
    ResendResolveResult resolve(ResendLookupContext context);
}
