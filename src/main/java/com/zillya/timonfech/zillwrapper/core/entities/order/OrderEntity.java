package com.zillya.timonfech.zillwrapper.core.entities.order;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.PaymentMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity implements IEntityWithStatus<OrderStatus> {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("capturedAt ASC")
    private List<OrderSourceContextEntity> sourceContexts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private ClientEntity client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDeliveryTargetEntity> deliveryTargets = new ArrayList<>(1);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_at_origin")
    private Instant createdAtOrigin;

    @Column(name = "updated_at_origin")
    private Instant updatedAtAtOrigin;

    @Column(name = "white_admin_id")
    private Long whiteAdminId;

    @Column(name = "portal_id", nullable = true)
    private Long portalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus;
    @Enumerated(EnumType.STRING)

    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(columnDefinition = "text")
    private String userComment;
    @Column(columnDefinition = "text")
    private String clientComment;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency")
    private CurrencyCode currency;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "http_ref", columnDefinition = "text")
    private String httpRef;

    @Column(name = "client_type", length = 255)
    private String clientType;

    @Column(name = "legal_entity_info_json", columnDefinition = "text")
    private String legalEntityInfoJson;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>(1);


    @Override
    public EntityTypeEnum getEntityType() {
        return EntityTypeEnum.ORDER;
    }

    @Override
    public OrderStatus getStatus() {
        return this.orderStatus;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
