package com.zillya.timonfech.zillwrapper.core.entities.security;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

/**
 * Stores granular, per-product or per-brand quotas for a user.
 * could be extended with max period per product functionality
 */
@Entity
@Table(name = "user_product_quotas")
@Getter
@Setter
@NoArgsConstructor
public class ProductQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;


    @ManyToOne
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    /**
     * Set of allowed business operations for this product.
     * Reuses standard enum OperationType.
     */
    @ElementCollection(targetClass = OperationType.class)
    @CollectionTable(name = "user_quota_operations", joinColumns = @JoinColumn(name = "quota_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private Set<OperationType> allowedOperations;

    /**
     * Maximum cumulative PC allowed in a single order item (count * pcPerLicense).
     */
    @Column(name = "max_total_pc")
    private Integer maxTotalPc;

    /**
     * Maximum PC allowed for a single license/key.
     */
    @Column(name = "max_pc_per_license")
    private Integer maxPcPerLicense;

    /**
     * Maximum number of keys allowed in one order item (count).
     */
    @Column(name = "max_total_licenses_per_item")
    private Integer maxTotalLicensesPerItem;

    @Column(name="max_period_amount")
    private Integer maxPeriodAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_period_unit")
    private BusinessPeriodUnit maxPeriodUnit;

    /**
     * Remaining quantity for active reservation (tokens/PC/count).
     */
    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    /**
     * Quantity currently reserved for pending orders.
     */
    @Column(name = "reserved_quantity")
    private Integer reservedQuantity = 0;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    public void reserve(int amount) {
        if (remainingQuantity != null) {
            this.remainingQuantity -= amount;
            this.reservedQuantity = (this.reservedQuantity == null ? 0 : this.reservedQuantity) + amount;
            this.lastUpdatedAt = Instant.now();
        }
    }

    public void confirm(int amount) {
        if (reservedQuantity != null) {
            this.reservedQuantity -= amount;
            this.lastUpdatedAt = Instant.now();
        }
    }

    public void rollback(int amount) {
        if (remainingQuantity != null && reservedQuantity != null) {
            this.remainingQuantity += amount;
            this.reservedQuantity -= amount;
            this.lastUpdatedAt = Instant.now();
        }
    }

    public void deduct(int amount) {
        if (remainingQuantity != null) {
            this.remainingQuantity -= amount;
            this.lastUpdatedAt = Instant.now();
        }
    }

    public void refund(int amount) {
        if (remainingQuantity != null) {
            this.remainingQuantity += amount;
            this.lastUpdatedAt = Instant.now();
        }
    }

    public BusinessPeriod getMaxPeriod() {
        if (maxPeriodAmount == null || maxPeriodUnit == null) {
            return null;
        }
        return new BusinessPeriod(maxPeriodAmount, maxPeriodUnit);
    }

    public void setMaxPeriod(BusinessPeriod maxPeriod) {
        if (maxPeriod == null) {
            this.maxPeriodAmount = null;
            this.maxPeriodUnit = null;
            return;
        }
        this.maxPeriodAmount = maxPeriod.amount();
        this.maxPeriodUnit = maxPeriod.unit();
    }
}
