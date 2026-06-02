package com.zillya.timonfech.zillwrapper.apis.sync.handlers;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.sync.EntitySyncHandler;
import com.zillya.timonfech.zillwrapper.apis.sync.SyncRequest;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOrderSyncHandler implements EntitySyncHandler {
    protected final OrderRepository orderRepository;
    protected final AbstractWhiteAdminClient client;

    @Override
    public boolean supports(SyncRequest request) {
        return request.entityType() == EntityTypeEnum.ORDER;
    }

    @Override
    public void sync(SyncRequest request) {
        OrderEntity order = orderRepository.findById(request.entityId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + request.entityId()));

        if (order.getWhiteAdminId() == null) {
            log.warn("Order {} has no whiteAdminId. Skipping sync.", order.getId());
            return;
        }

        doSync(order);
    }

    protected abstract void doSync(OrderEntity order);
}
