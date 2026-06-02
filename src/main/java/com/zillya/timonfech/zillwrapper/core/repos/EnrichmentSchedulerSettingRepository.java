package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.enrichment.EnrichmentSchedulerSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrichmentSchedulerSettingRepository extends JpaRepository<EnrichmentSchedulerSettingEntity, String> {
}
