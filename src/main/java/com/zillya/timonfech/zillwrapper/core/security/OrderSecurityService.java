package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.security.ProductQuotaEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodNormalizer;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSecurityService {

    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final BusinessPeriodNormalizer periodNormalizer;

    public void checkGeneralAccess(UserEntity initiator) {

        if (!initiator.isActive()) {
            throw new SecurityException("User " + initiator.getUsername() + " is inactive");
        }
        if (initiator.getRole() == null || initiator.getRole() == UserEntity.Role.NONE) {
            throw new SecurityException("User " + initiator.getUsername() + " has no access role");
        }

        log.debug("General access verified for user: {}", initiator.getUsername());
    }

    public void checkOrder(OrderOperationContext context) {
        if (context.getItemSpecs() == null || context.getItemSpecs().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        for (OrderItemSpec item : context.getItemSpecs()) {
            if (item.product() == null) {
                throw new IllegalArgumentException("Product cannot be null");
            }
            if (item.count() <= 0) {
                throw new IllegalArgumentException("Count must be greater than zero");
            }
            if (item.computers() <= 0) {
                throw new IllegalArgumentException("PC per license must be greater than zero");
            }
            if (item.period() != null && periodNormalizer.toDays(item.period()) > 365L * 7L) {
                throw new IllegalArgumentException("Period cannot be more than 7 years");
            }
        }
    }

    public void checkOrderQuota(UserEntity initiator, OrderOperationContext context) {
        if (initiator.getRole() == UserEntity.Role.ADMIN)
            return;

        log.debug("Auditing order quotas for User={}", initiator.getUsername());

        for (OrderItemSpec item : context.getItemSpecs()) {
            ProductQuotaEntity quota = findQuota(initiator, item);

            if (quota == null) {
                throw new SecurityException("No quota defined for product: " + item.product().productId());
            }

            // 1. Operation Check
            if (quota.getAllowedOperations() == null
                    || !quota.getAllowedOperations().contains(OperationType.ORDER_CREATION)) {
                throw new SecurityException(
                        "Operation ORDER_CREATION is not allowed for product " + item.product().productId());
            }

            // 2. PC per License Check
            if (quota.getMaxPcPerLicense() != null && item.computers() > quota.getMaxPcPerLicense()) {
                throw new SecurityException(
                        "PC per license (" + item.computers() + ") exceeds limit (" + quota.getMaxPcPerLicense() + ")");
            }

            // 3. Total PC Check
            int requestedTotalPc = item.count() * item.computers();
            if (quota.getMaxTotalPc() != null && requestedTotalPc > quota.getMaxTotalPc()) {
                throw new SecurityException(
                        "Total requested PC (" + requestedTotalPc + ") exceeds limit (" + quota.getMaxTotalPc() + ")");
            }

            // 4. Quantity Check
            if (quota.getMaxTotalLicensesPerItem() != null && item.count() > quota.getMaxTotalLicensesPerItem()) {
                throw new SecurityException("License count (" + item.count() + ") exceeds limit ("
                        + quota.getMaxTotalLicensesPerItem() + ")");
            }

            // 5. Period Check
            if (item.period() != null && quota.getMaxPeriod() != null) {
                if (periodNormalizer.toDays(item.period()) > periodNormalizer.toDays(quota.getMaxPeriod())) {
                    throw new SecurityException(
                            "Requested period exceeds maximum for product " + item.product().productId());
                }
            }

            // 6. Remaining Quota Check (Dry Run)
            if (quota.getRemainingQuantity() != null && quota.getRemainingQuantity() < requestedTotalPc) {
                throw new SecurityException("Insufficient remaining quota for product " + item.product().productId());
            }
        }
    }

    @Transactional
    public void reserveQuota(Long userId, Long orderId) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        if (user.getRole() == UserEntity.Role.ADMIN)
            return;

        log.info("Reserving quotas for User={} Order={}", user.getUsername(), orderId);
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);

        for (OrderItemEntity item : items) {
            ProductQuotaEntity quota = user.getQuotas().stream()
                    .filter(q -> q.getProduct() != null && q.getProduct().getProductId() == item.getProductId())
                    .findFirst()
                    .orElse(null);

            if (quota != null) {
                int amount = item.getCount() * item.getPcPerLicense();
                quota.reserve(amount);
            }
        }
        userRepository.save(user);
    }

    @Transactional
    public void confirmQuota(Long userId, Long orderId) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        if (user.getRole() == UserEntity.Role.ADMIN)
            return;

        log.info("Confirming quotas for User={} Order={}", user.getUsername(), orderId);
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);

        for (OrderItemEntity item : items) {
            ProductQuotaEntity quota = user.getQuotas().stream()
                    .filter(q -> q.getProduct() != null && q.getProduct().getProductId() == item.getProductId())
                    .findFirst()
                    .orElse(null);

            if (quota != null) {
                int amount = item.getCount() * item.getPcPerLicense();
                quota.confirm(amount);
            }
        }
        userRepository.save(user);
    }

    @Transactional
    public void rollbackQuota(Long userId, Long orderId) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        if (user.getRole() == UserEntity.Role.ADMIN)
            return;

        log.info("Rolling back quotas for User={} Order={}", user.getUsername(), orderId);
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);

        for (OrderItemEntity item : items) {
            ProductQuotaEntity quota = user.getQuotas().stream()
                    .filter(q -> q.getProduct() != null && q.getProduct().getProductId() == item.getProductId())
                    .findFirst()
                    .orElse(null);

            if (quota != null) {
                int amount = item.getCount() * item.getPcPerLicense();
                quota.rollback(amount);
            }
        }
        userRepository.save(user);
    }

    private ProductQuotaEntity findQuota(UserEntity user, OrderItemSpec item) {
        return user.getQuotas().stream()
                .filter(q -> q.getProduct() != null && q.getProduct().getProductId() == item.product().productId())
                .findFirst()
                .orElse(null);
    }
}
