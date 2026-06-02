package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ip_contact", indexes = @Index(name = "idx_ip_value_hash", columnList = "valueHash"))
@NoArgsConstructor
public class IpContact extends ContactMethod{

    public IpContact(
            String ip
    ) {
        this.plainValue = ip;
    }
    public String encryptedValue;

    @Column(length = 256)
    public String valueHash;

    @Transient
    @Getter
    public String plainValue;

    @Override
    public void prepareForPersist(CryptoUtils crypto) throws Exception {
        if (plainValue == null) return;
        this.valueHash = null;
        this.encryptedValue = plainValue.trim();
        this.plainValue = null;
    }
}
