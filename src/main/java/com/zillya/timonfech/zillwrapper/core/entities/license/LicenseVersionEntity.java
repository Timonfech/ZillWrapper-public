package com.zillya.timonfech.zillwrapper.core.entities.license;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "license_versions")
@Getter
@Setter
public class LicenseVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id")
    private Long licenseId;
    @Column(name = "version_no")
    private Long versionNo;
    @Column(name = "changed_at")
    private Instant changedAt;
    @Column(name = "change_source")
    private String changeSource;
    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "external_id")
    private Long externalId;
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "order_item_id")
    private Long orderItemId;
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "period_amount")
    private Integer periodAmount;
    @Enumerated(EnumType.STRING)
    @Column(name = "period_unit")
    private BusinessPeriodUnit periodUnit;
    private Integer devices;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "created_at_origin")
    private Instant createdAtOrigin;
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.ORDINAL)
    private LicenseStatus status;
    private String description;
    @Column(name = "source_id")
    private Long sourceId;
}
