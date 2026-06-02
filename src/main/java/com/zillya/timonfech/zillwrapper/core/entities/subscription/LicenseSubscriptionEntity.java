package com.zillya.timonfech.zillwrapper.core.entities.subscription;

import com.zillya.timonfech.zillwrapper.core.subscription.SubscriptionLeadUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "license_subscription_entity")
public class LicenseSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id", nullable = false)
    private Long licenseId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "initiator_user_id")
    private Long initiatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "detailed")
    private Boolean detailed;

    @Column(name = "notify_client")
    private Boolean notifyClient;

    @Column(name = "warning_lead_amount")
    private Integer warningLeadAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "warning_lead_unit", length = 16)
    private SubscriptionLeadUnit warningLeadUnit;

    @Column(name = "check_interval_minutes")
    private Integer checkIntervalMinutes;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "expected_expiration")
    private Instant expectedExpiration;

    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum SubscriptionStatus {
        ACTIVE,
        UNSUBSCRIBED,
        WARNING_SENT,
        EXPIRED,
        CANCELLED,
        ERROR
    }
}
