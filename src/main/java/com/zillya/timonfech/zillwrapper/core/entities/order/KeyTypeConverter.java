package com.zillya.timonfech.zillwrapper.core.entities.order;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class KeyTypeConverter implements AttributeConverter<List<KeyType>, String> {
    @Override
    public String convertToDatabaseColumn(List<KeyType> attribute) {
        return attribute == null ? null : attribute.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<KeyType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
        return Arrays.stream(dbData.split(","))
                .map(KeyType::valueOf)
                .collect(Collectors.toList());
    }
}