package com.zillya.timonfech.zillwrapper.core.entities.operation;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "operation_execution")
@Getter
@Setter
public class OperationExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private BigInteger id;

    private Long sourceId;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    private OperationExecutionKind executionKind = OperationExecutionKind.STAGE;

    @Enumerated(EnumType.STRING)
    private EntityTypeEnum entityTypeEnum;

    @Nullable
    @Column(nullable = true)
    private Long entityId;

    private String handlerUUID;
    private String handlerName;

    @Enumerated(EnumType.STRING)
    private OperationStatus status;

    private int attempt;
    private boolean recoverable;
    private boolean cancelable;
    private boolean interactionEnabled;

    private String errorMessage;

    private String questionType;
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String questionJson;
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String executionPlanJson;
    private Integer sequenceNo;
    private boolean nonBlocking;

    private Instant resumeAt;
    
    private Long initiatorUserId;
    private BigInteger parentId;

    @Version
    @Column(name = "state_version")
    private Long stateVersion;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); updatedAt = Instant.now(); } //status = OperationStatus.RUNNING;
    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
}
