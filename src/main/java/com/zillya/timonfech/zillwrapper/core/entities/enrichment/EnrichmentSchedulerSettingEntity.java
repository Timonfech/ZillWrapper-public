package com.zillya.timonfech.zillwrapper.core.entities.enrichment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "enrichment_scheduler_settings")
@Getter
@Setter
public class EnrichmentSchedulerSettingEntity {
    @Id
    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "delay_minutes", nullable = false)
    private int delayMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;
}
