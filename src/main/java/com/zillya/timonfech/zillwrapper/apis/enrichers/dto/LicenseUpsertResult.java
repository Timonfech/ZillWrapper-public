package com.zillya.timonfech.zillwrapper.apis.enrichers.dto;

public record LicenseUpsertResult(
        Long licenseId,
        Long clientId,
        Long orderId,
        boolean licenseChanged,
        boolean clientChanged,
        boolean orderChanged,
        boolean stopScanning
) {
}