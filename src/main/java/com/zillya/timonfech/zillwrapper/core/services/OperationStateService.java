package com.zillya.timonfech.zillwrapper.core.services;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Service
@RequiredArgsConstructor
public class OperationStateService {

    private final OperationExecutionService operationService;


    public OperationStatus getStatus(BigInteger operationId) {
        return operationService.getStatus(operationId);
    }


    public boolean isCancelled(BigInteger operationId) {
        return getStatus(operationId) == OperationStatus.CANCELLED;
    }


    public boolean isWaiting(BigInteger operationId) {
        return getStatus(operationId) == OperationStatus.WAITING_INTERACTION;
    }


    public boolean canProceed(BigInteger operationId) {
        OperationStatus status = getStatus(operationId);
        return status == OperationStatus.RUNNING || status == OperationStatus.RESUME;
    }
}
