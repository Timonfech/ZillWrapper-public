package com.zillya.timonfech.zillwrapper.apis.sync;

import com.zillya.timonfech.zillwrapper.apis.EntityUpdate;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;

public class OrderSync implements EntityUpdate {
    @Override
    public IEntityWithStatus<?> targetType() {
        return null;
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public boolean supports(Object event) {
        return false;
    }

    @Override
    public OperationResult<?> handle(Object o) throws OperationCancelledException {
        return null;
    }
}
