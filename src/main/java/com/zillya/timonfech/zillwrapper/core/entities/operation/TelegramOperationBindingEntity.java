package com.zillya.timonfech.zillwrapper.core.entities.operation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "telegram_operation_bindings")
@Getter
@Setter
public class TelegramOperationBindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigInteger operationId;

    private Long chatId;
    private Integer controlMessageId;

    @jakarta.persistence.Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String questionQueueJson;

    private String activePreviewId;
    private Integer previewMessageId;
    private Integer sourceMessageId;
    private String sourceMessageHash;

    @jakarta.persistence.Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String previewPayloadJson;

    private Instant previewCreatedAt;
    private Instant previewExpiresAt;
    private String previewStatus;
    private String interactionDeliveryStatus;
    private String localeTag;
    private Instant finalNotifiedAt;
    private String finalNotificationKind;

    @Version
    private Long version;
}
