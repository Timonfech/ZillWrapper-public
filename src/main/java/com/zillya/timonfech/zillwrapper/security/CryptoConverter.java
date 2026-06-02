package com.zillya.timonfech.zillwrapper.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;


@Converter(autoApply = false)
public class CryptoConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            return CryptoHolder.get().encryptToBase64(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return CryptoHolder.get().decryptFromBase64(dbData);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
