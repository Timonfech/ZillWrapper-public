package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateRoutingService {

    private final EmailRoutingProperties properties;

    public ResolvedEmailTemplate resolve(OperationType rootOperationType,
                                         OrderDeliveryTargetEntity target,
                                         OrderItemEntity item) {
        ContactMethodType resolvedType = resolveContactType(target);
        List<EmailRoutingProperties.Rule> matches = new ArrayList<>();
        for (EmailRoutingProperties.Rule rule : properties.getRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (rule.getOperationType() != null && rule.getOperationType() != rootOperationType) {
                continue;
            }
            if (rule.getContactType() != null && rule.getContactType() != resolvedType) {
                continue;
            }
            matches.add(rule);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple email routing rules matched opType="
                    + rootOperationType + ", contactType=" + resolvedType + ", rules="
                    + matches.stream().map(EmailRoutingProperties.Rule::getId).toList());
        }
        if (matches.size() == 1) {
            EmailRoutingProperties.Rule rule = matches.getFirst();
            log.info("Email routing matched rule={} opType={} contactType={} itemId={}",
                    rule.getId(),
                    rootOperationType,
                    resolvedType,
                    item == null ? null : item.getId());
            return new ResolvedEmailTemplate(
                    rule.getId(),
                    rule.getTemplate(),
                    rule.getSubjectKeys().getSingle(),
                    rule.getSubjectKeys().getPlural(),
                    rule.getSubjectKeys().getOfflineSuffix()
            );
        }

        throw new IllegalStateException("No email routing rule matched opType="
                + rootOperationType + ", contactType=" + resolvedType);
    }

    private ContactMethodType resolveContactType(OrderDeliveryTargetEntity target) {
        if (target == null || target.getContactMethod() == null) {
            return null;
        }
        ContactMethodType type = target.getContactMethod().getType();
        if (type != null) {
            return type;
        }
        if (target.getContactMethod() instanceof com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact) {
            return ContactMethodType.EMAIL;
        }
        return null;
    }
}
