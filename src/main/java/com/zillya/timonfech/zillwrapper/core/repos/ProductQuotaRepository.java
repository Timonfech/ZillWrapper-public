package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.product.ProductEntity;
import com.zillya.timonfech.zillwrapper.core.entities.security.ProductQuotaEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductQuotaRepository extends JpaRepository<ProductQuotaEntity, Long> {
    Optional<ProductQuotaEntity> findByUserAndProduct(UserEntity user, ProductEntity product);
}
