package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class EmailContact extends ContactMethod {

    public EmailContact(
            String emailAddr
    ) {
        plainValue = emailAddr;
    }
    public String encryptedValue;

    @Column(length = 256)
    public String valueHash;

    @Transient
    public String plainValue;

    @Override
    public void prepareForPersist(CryptoUtils crypto) throws Exception {
        if (plainValue == null) return;
        this.valueHash = null;
        this.encryptedValue = plainValue.trim();
        this.plainValue = null;
    }
}
