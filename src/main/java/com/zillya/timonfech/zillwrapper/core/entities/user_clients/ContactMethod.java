package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Setter
@Getter
@DiscriminatorColumn(name = "contact_kind", discriminatorType = DiscriminatorType.STRING)
public abstract class ContactMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    public ContactMethodType type;

    public String label;

    public Boolean preferred = false;

    @ManyToOne
    public ClientEntity client;

    @ManyToOne
    public UserEntity user;

    public abstract void prepareForPersist(CryptoUtils crypto) throws Exception;

    @PrePersist
    @PreUpdate
    protected void prepareForLifecyclePersist() {
        try {
            prepareForPersist(null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare contact for persistence: " + getClass().getSimpleName(), ex);
        }
    }
}
