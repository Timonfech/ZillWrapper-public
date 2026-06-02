package com.zillya.timonfech.zillwrapper.core.entities.license;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class WhiteAdminActivationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pcid;
    private Instant firstActivation;
    private Instant lastActivation;
    private Integer computersActivated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_admin_key_id")
    private WhiteAdminKeyEntity whiteAdminKey;
}

