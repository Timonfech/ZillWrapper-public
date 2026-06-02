package com.zillya.timonfech.zillwrapper.core.entities.pending;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(name = "pending_tasks")
@Getter
@Setter
public class PendingTaskEntity {

    @Id
    @Column(name = "task_id", length = 64)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingTaskStatus status;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long initiatorUserId;

    @Column(length = 128)
    private String sourceActorId;

    @Column(nullable = false)
    private String payloadType;

    @Column(columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String payloadJson;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Version
    private Long version;
}
