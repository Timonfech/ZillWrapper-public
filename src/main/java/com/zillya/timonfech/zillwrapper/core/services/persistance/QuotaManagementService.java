package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductEntity;
import com.zillya.timonfech.zillwrapper.core.entities.security.ProductQuotaEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.repos.ProductQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuotaManagementService {

    private final ProductQuotaRepository quotaRepository;


    @Transactional
    public void reserveQuota(UserEntity user, ProductEntity product, OperationType operation, int amountToDeduct) {
        ProductQuotaEntity quota = quotaRepository.findByUserAndProduct(user, product)
                .orElseThrow(() -> new IllegalStateException("No quota found for user and product"));

        if (!quota.getAllowedOperations().contains(operation)) {
            throw new IllegalStateException("Operation " + operation + " is not allowed for this quota");
        }

        if (quota.getRemainingQuantity() != null && quota.getRemainingQuantity() < amountToDeduct) {
            throw new IllegalStateException("Insufficient quota remaining");
        }

        quota.deduct(amountToDeduct);
    }

    @Transactional
    public void refundQuota(UserEntity user, ProductEntity product, int amountToRefund) {
        ProductQuotaEntity quota = quotaRepository.findByUserAndProduct(user, product)
                .orElseThrow(() -> new IllegalStateException("No quota found for user and product"));

        quota.refund(amountToRefund);
    }
}