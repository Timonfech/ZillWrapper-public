package com.zillya.timonfech.zillwrapper.apis.enrichers.dto;

public record OrderUpsertResult(
        Long orderId,
        Long clientId,
        boolean orderChanged,
        boolean clientChanged,
        boolean stopScanning
) {
}