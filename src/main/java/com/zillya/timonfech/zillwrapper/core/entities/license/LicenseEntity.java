package com.zillya.timonfech.zillwrapper.core.entities.license;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "licenses")
@Getter
@Setter
public class LicenseEntity implements IEntityWithStatus<LicenseStatus> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    Long externalId;

    Long orderId;
    Long orderItemId;
    Long clientId;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "key_id")
    BaseKeyEntity key;

    Integer periodAmount;
    @Enumerated(EnumType.STRING)
    BusinessPeriodUnit periodUnit;

    Integer brandId;
    Integer productId;
    Integer devices;

    Instant createdAt;
    Instant createdAtOrigin;
    Instant expiresAt;

    @Enumerated(EnumType.ORDINAL)
    LicenseStatus status;
    String description;
    @Column(name = "version_no", nullable = false)
    Long versionNo = 1L;

    @Column(name = "source_id")
    Long sourceId;

    public BusinessPeriod getBusinessPeriod() {
        if (periodAmount == null || periodUnit == null) {
            return null;
        }
        return new BusinessPeriod(periodAmount, periodUnit);
    }

    public void setBusinessPeriod(BusinessPeriod period) {
        if (period == null) {
            this.periodAmount = null;
            this.periodUnit = null;
            return;
        }
        this.periodAmount = period.amount();
        this.periodUnit = period.unit();
    }

    @Override
    public EntityTypeEnum getEntityType() {
        return EntityTypeEnum.LICENSE;
    }
}
