package com.zillya.timonfech.zillwrapper.apis.enrichers.dto;

public record LegalEntityInfo(
        String TIN,
        String companyName,
        String physicalAddress,
        String legalAddress
) {
}