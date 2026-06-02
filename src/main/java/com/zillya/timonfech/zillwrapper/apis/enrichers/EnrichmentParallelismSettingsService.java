package com.zillya.timonfech.zillwrapper.apis.enrichers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EnrichmentParallelismSettingsService {

    private final AtomicInteger licenseParallelism = new AtomicInteger(1);
    private final AtomicInteger activationsParallelism = new AtomicInteger(1);

    public EnrichmentParallelismSettingsService(
            @Value("${enrichment.parallelism.license:${enrichment.parallelism:1}}") int licenseParallelism,
            @Value("${enrichment.parallelism.activations:${enrichment.parallelism:1}}") int activationsParallelism
    ) {
        this.licenseParallelism.set(Math.max(1, licenseParallelism));
        this.activationsParallelism.set(Math.max(1, activationsParallelism));
    }

    public int getLicenseParallelism() {
        return licenseParallelism.get();
    }

    public int getActivationsParallelism() {
        return activationsParallelism.get();
    }

    public void setLicenseParallelism(int value) {
        licenseParallelism.set(Math.max(1, value));
    }

    public void setActivationsParallelism(int value) {
        activationsParallelism.set(Math.max(1, value));
    }
}
