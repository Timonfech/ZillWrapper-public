package com.zillya.timonfech.zillwrapper.apis.enrichers;

public interface ActivationProvider {
    boolean supports(ActivationProviderType providerType);
    void enrich(Long keyId, Long externalId, Integer productId) throws Exception;
}
