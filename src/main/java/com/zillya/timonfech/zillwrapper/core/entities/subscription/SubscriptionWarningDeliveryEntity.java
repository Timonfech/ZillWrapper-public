package com.zillya.timonfech.zillwrapper.core.entities.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "subscription_warning_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_warning_delivery_window",
                columnNames = {"subscription_id", "window_at"}
        )
)
public class SubscriptionWarningDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "license_id", nullable = false)
    private Long licenseId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "window_at", nullable = false)
    private Instant windowAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum DeliveryStatus {
        PENDING,
        SENT,
        FAILED,
        GAVE_UP,
        DEFERRED_NO_EXTERNAL_ID
    }
}
