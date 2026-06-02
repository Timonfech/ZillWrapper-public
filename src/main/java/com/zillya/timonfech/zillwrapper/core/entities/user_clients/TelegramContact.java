package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class TelegramContact extends ContactMethod {
    public String encryptedTelegramId;

    public String encryptedUsername;

    public String encryptedTgChatId;

    @Column(length = 256)
    public String valueHash;

    @Transient
    public String plainTelegramId;
    @Transient
    public String plainUsername;
    @Transient
    public String plainTgChatId;

    @Override
    public void prepareForPersist(CryptoUtils crypto) throws Exception {
        this.valueHash = null;
        if (plainTelegramId != null) {
            this.encryptedTelegramId = plainTelegramId;
            this.plainTelegramId = null;
        }
        if (plainUsername != null) {
            this.encryptedUsername = plainUsername;
            this.plainUsername = null;
        }
        if (plainTgChatId != null) {
            this.encryptedTgChatId = plainTgChatId;
            this.plainTgChatId = null;
        }
    }
}
