package com.zillya.timonfech.zillwrapper.core.entities.order;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "order_source_contexts")
@Getter
@Setter
@NoArgsConstructor
public class OrderSourceContextEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, insertable = false, updatable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Column(name = "operation_id")
    private BigInteger operationId;

    private Long userId;


    private Instant capturedAt;


    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String contextData;

}
