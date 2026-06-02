package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import jakarta.persistence.*;

@Entity
@Table(name = "phone_contact", indexes = @Index(name = "idx_phone_value_hash", columnList = "valueHash"))
public class PhoneContact extends ContactMethod {
    public String encryptedValue;

    @Column(length = 256)
    public String valueHash;

    @Transient
    public String plainValue;

    @Override
    public void prepareForPersist(CryptoUtils crypto) throws Exception {
        if (plainValue == null) return;
        this.valueHash = null;
        this.encryptedValue = plainValue;
        this.plainValue = null;
    }
}
