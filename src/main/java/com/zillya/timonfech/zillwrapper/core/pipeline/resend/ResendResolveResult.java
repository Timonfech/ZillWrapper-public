package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

public record ResendResolveResult(
        ResendResolveStatus status,
        ResolvedResendTarget target,
        String reason
) {
    public static ResendResolveResult found(ResolvedResendTarget target) {
        return new ResendResolveResult(ResendResolveStatus.FOUND, target, null);
    }

    public static ResendResolveResult notFound(String reason) {
        return new ResendResolveResult(ResendResolveStatus.NOT_FOUND, null, reason);
    }

    public static ResendResolveResult ambiguous(String reason) {
        return new ResendResolveResult(ResendResolveStatus.AMBIGUOUS, null, reason);
    }
}
