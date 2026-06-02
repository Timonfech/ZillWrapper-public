package com.zillya.timonfech.zillwrapper.core.communication;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class EmailSendResult {
    private final Set<Long> deliveredItemIds = new HashSet<>();
    private final Set<Long> failedItemIds = new HashSet<>();
    private final List<String> errors = new ArrayList<>();

    public void markDelivered(Long itemId) {
        if (itemId != null) {
            deliveredItemIds.add(itemId);
        }
    }

    public void markFailed(Long itemId, String error) {
        if (itemId != null) {
            failedItemIds.add(itemId);
        }
        if (error != null && !error.isBlank()) {
            errors.add(error);
        }
    }
}
