package com.zillya.timonfech.zillwrapper.core.entities.order;


import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private OrderEntity order;

    @Column(name = "product_brand_id")
    private Integer productBrandId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "pc_per_license")
    private Integer pcPerLicense;

    @Column(name = "lic_count")
    private Integer count;

    @Column(name = "period_amount")
    private Integer periodAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_unit")
    private BusinessPeriodUnit periodUnit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_types", columnDefinition = "jsonb")
    private List<KeyType> keyTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_types", columnDefinition = "jsonb")
    private List<OutputType> outputTypes;

    @Column(name = "server_number")
    private Integer serverNumber;

    /**
     * Durable per-item processing status.
     * ISource of truth for pipeline progress and quota refund decisions.
     * See {@link ItemProcessingStatus} for state machine rules.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ItemProcessingStatus processingStatus = ItemProcessingStatus.PENDING;

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
}
