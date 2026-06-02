package com.zillya.timonfech.zillwrapper.core.entities.operation;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class OperationTypeListConverter implements AttributeConverter<List<OperationType>, String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(List<OperationType> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream()
                .map(OperationType::name)
                .collect(Collectors.joining(DELIMITER));
    }

    @Override
    public List<OperationType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(dbData.split(DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OperationType::valueOf)
                .toList();
    }
}
