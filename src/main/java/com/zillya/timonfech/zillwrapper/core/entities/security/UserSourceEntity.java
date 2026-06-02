package com.zillya.timonfech.zillwrapper.core.entities.security;

import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Represents an authorized source configuration for a user.
 * A single user can have multiple sources (Telegram account, API client, etc.).
 * To be authenticated, a request must satisfy ALL factors in this configuration.
 */
@Entity
@Table(name = "user_sources")
@Getter
@Setter
@NoArgsConstructor
public class UserSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private SourceEntity source;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    /**
     * Supported identification factors for multi-factor source verification.
     */
    public enum SecurityFactor {

        TELEGRAM_ID,
        /** Display username without @ */
        TELEGRAM_NICKNAME,

        API_USERNAME,

        API_KEY_HASH,
        API_KEY_SALT,
        PLAIN_API_KEY
    }

    /**
     * Set of identifying factors that MUST be present and match.
     */
    @ElementCollection
    @CollectionTable(name = "user_source_factors", joinColumns = @JoinColumn(name = "user_source_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_source_factor_value",
                    columnNames = {"factor_type", "factor_value"})
    )
    @MapKeyColumn(name = "factor_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "factor_value")
    private Map<SecurityFactor, String> requiredFactors;

    public UserSourceEntity(UserEntity user, SourceType sourceType, Map<SecurityFactor, String> factors) {
        this.user = user;
        this.sourceType = sourceType;
        this.requiredFactors = factors;
    }
}
