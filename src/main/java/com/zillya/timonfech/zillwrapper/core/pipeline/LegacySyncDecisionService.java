package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.PaymentMethod;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import org.springframework.stereotype.Service;

@Service
public class LegacySyncDecisionService {

    public LegacySyncDecision decide(OrderEntity order) {
        if (order == null) {
            return new LegacySyncDecision(false, false, "ORDER_NOT_FOUND");
        }
        if (order.getWhiteAdminId() == null) {
            return new LegacySyncDecision(false, false, "NO_WHITE_ADMIN_ID");
        }
        if (order.getPaymentMethod() == PaymentMethod.INVOICE) {
            return new LegacySyncDecision(true, false, "TODO_INVOICE_POLICY");
        }
        if (order.getOrderStatus() == OrderStatus.PAYED) {
            return new LegacySyncDecision(true, true, null);
        }
        return new LegacySyncDecision(true, false, "ORDER_NOT_PAYED");
    }
}
