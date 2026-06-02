package com.zillya.timonfech.zillwrapper.core.regex.order;

public record ParsedOrderReference(Long portalId,
                                   Long whiteAdminId,
                                   String userComment,
                                   String docAddress,
                                   String waComment) {
}
