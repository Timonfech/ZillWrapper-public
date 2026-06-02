package com.zillya.timonfech.zillwrapper.core.entities.license;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class LicenseActivationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String activationId;
    private String productId;
    private String externalLicenseId;
    private String hardware;
    private String profileId;
    private Instant created;
    private Instant lastRequest;
    private String lastIp;
    private String lastSuccess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dino_key_id")
    private DinoKeyEntity dinoKey;
}
