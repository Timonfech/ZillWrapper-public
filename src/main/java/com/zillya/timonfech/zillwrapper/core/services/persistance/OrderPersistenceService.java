package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("orderPersistenceService")
@RequiredArgsConstructor
@Slf4j
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderEntity saveOrUpdate(OrderEntity orderEntity) {
        if (orderEntity == null) {
            return null;
        }
        OrderEntity existing = findExisting(orderEntity);
        if (existing != null) {
            applyPatch(existing, orderEntity);
            log.info("order_reused_by_ref localOrderId={} whiteAdminId={} portalId={}",
                    existing.getId(), existing.getWhiteAdminId(), existing.getPortalId());
            return orderRepository.save(existing);
        }

        try {
            OrderEntity created = orderRepository.save(orderEntity);
            log.info("order_created localOrderId={} whiteAdminId={} portalId={}",
                    created.getId(), created.getWhiteAdminId(), created.getPortalId());
            return created;
        } catch (DataIntegrityViolationException ex) {
            // Race between parallel enrichers for the same WA/portal ref:
            // another transaction may have inserted the row first.
            OrderEntity resolved = findExisting(orderEntity);
            if (resolved == null) {
                throw ex;
            }
            applyPatch(resolved, orderEntity);
            log.info("order_unique_conflict_recovered localOrderId={} whiteAdminId={} portalId={}",
                    resolved.getId(), resolved.getWhiteAdminId(), resolved.getPortalId());
            return orderRepository.save(resolved);
        }
    }

    private OrderEntity findExisting(OrderEntity incoming) {
        if (incoming.getWhiteAdminId() != null) {
            return orderRepository.findByWhiteAdminId(incoming.getWhiteAdminId()).orElse(null);
        }
        if (incoming.getPortalId() != null) {
            return orderRepository.findByPortalId(incoming.getPortalId()).orElse(null);
        }
        return null;
    }

    private void applyPatch(OrderEntity target, OrderEntity source) {
        if (source.getClient() != null) {
            target.setClient(source.getClient());
        }
        if (source.getCreatedBy() != null) {
            target.setCreatedBy(source.getCreatedBy());
        }
        if (source.getCreatedAt() != null && target.getCreatedAt() == null) {
            target.setCreatedAt(source.getCreatedAt());
        }
        if (source.getCreatedAtOrigin() != null) {
            target.setCreatedAtOrigin(source.getCreatedAtOrigin());
        }
        if (source.getUpdatedAtAtOrigin() != null) {
            target.setUpdatedAtAtOrigin(source.getUpdatedAtAtOrigin());
        }
        if (source.getWhiteAdminId() != null) {
            target.setWhiteAdminId(source.getWhiteAdminId());
        }
        if (source.getPortalId() != null) {
            target.setPortalId(source.getPortalId());
        }
        if (source.getOrderStatus() != null && target.getOrderStatus() == null) {
            target.setOrderStatus(source.getOrderStatus());
        }
        if (source.getPaymentMethod() != null) {
            target.setPaymentMethod(source.getPaymentMethod());
        }
        if ((target.getUserComment() == null || target.getUserComment().isBlank())
                && source.getUserComment() != null
                && !source.getUserComment().isBlank()) {
            target.setUserComment(source.getUserComment());
        }
        if (source.getClientComment() != null && !source.getClientComment().isBlank()) {
            target.setClientComment(source.getClientComment());
        }
        if (source.getTotalAmount() != null) {
            target.setTotalAmount(source.getTotalAmount());
        }
        if (source.getCurrency() != null) {
            target.setCurrency(source.getCurrency());
        }
        if (source.getExternalRef() != null && !source.getExternalRef().isBlank()) {
            target.setExternalRef(source.getExternalRef());
        }
        if (source.getHttpRef() != null && !source.getHttpRef().isBlank()) {
            target.setHttpRef(source.getHttpRef());
        }
        if (source.getClientType() != null && !source.getClientType().isBlank()) {
            target.setClientType(source.getClientType());
        }
        if (source.getLegalEntityInfoJson() != null && !source.getLegalEntityInfoJson().isBlank()) {
            target.setLegalEntityInfoJson(source.getLegalEntityInfoJson());
        }
    }

}
