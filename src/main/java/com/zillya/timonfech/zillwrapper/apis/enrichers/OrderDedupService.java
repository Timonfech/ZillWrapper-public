package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.OrderAggregate;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.OrderUpsertResult;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.IpContact;
import com.zillya.timonfech.zillwrapper.core.repos.ClientRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.services.persistance.ContactManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDedupService {

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final ContactManagementService contactManagementService;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

//    @Transactional
//    public OrderUpsertResult upsert(EnrichEvent ctx, OrderAggregate aggregate) {
//        OrderEntity incomingOrder = aggregate.order();
//
//        ClientEntity savedClient = null;
//        boolean clientChanged = false;
//
//        if (aggregate.client() != null) {
//            // ASSUMES THAT IT IS RUNTIME OBJ! AND PLAIN VALUE IS SET
//            UserEntity user = aggregate.client().contacts.stream()
//                    .filter(cm -> cm instanceof EmailContact)
//                    .map(ec -> {
//                                try {
//                                    return contactManagementService.getEmailContactByEmail(((EmailContact) ec).getPlainValue().trim());
//                                } catch (Exception e) {
//                                    throw new RuntimeException(e);
//                                }
//                            }
//                    ).filter(Optional::isPresent)
//                    .map(Optional::get)
//                    .map(ec -> userRepository.findByContacts(List.of(ec)))
//                    .filter(Optional::isPresent)
//                    .map(Optional::get).findFirst().orElse(null);
//            if (){
//                new UserEntity().
//            }
//            if()
//            savedClient = clientRepository.save(aggregate.client());
//
//            // Link contacts if present
//            if (aggregate.contacts() != null && !aggregate.contacts().isEmpty()) {
//                final ClientEntity finalSavedClient = savedClient;
//                aggregate.contacts().forEach(contact -> contact.setClient(finalSavedClient));
//            }
//            clientChanged = true;
//        }
//
//        OrderEntity targetOrder = null;
//        boolean stopScanning = false;
//        boolean orderInfoUpdated = false;
//
//        if (ctx.getEntityId() != null) {
//            targetOrder = orderRepository.findById(ctx.getEntityId()).orElse(null);
//        }
//
//        if (targetOrder == null) {
//            targetOrder = findExistingByIncoming(incomingOrder);
//        }
//
//        if (targetOrder == null) {
//            targetOrder = new OrderEntity();
//        } else {
//            if (targetOrder.getWhiteAdminId() != null) {
//                stopScanning = true;
//            }
//        }
//
//        // Link client to order
//        if (savedClient != null) {
//            incomingOrder.setClient(savedClient);
//        }
//
//        mergeOrder(targetOrder, incomingOrder);
//        targetOrder = orderRepository.save(targetOrder);
//        orderInfoUpdated = true;
//
//        // 3. Process Order Items
//        if (aggregate.items() != null && !aggregate.items().isEmpty()) {
//            for (OrderItemEntity item : aggregate.items()) {
//                item.setOrderId(targetOrder.getId());
//                orderItemRepository.save(item);
//            }
//        }
//
//        return new OrderUpsertResult(
//                targetOrder.getId(),
//                savedClient != null ? savedClient.getId() : null,
//                orderInfoUpdated,
//                clientChanged,
//                stopScanning
//        );
//    }
@Transactional
public OrderUpsertResult upsert(EnrichmentRequest ctx, OrderAggregate aggregate) {
    OrderEntity incomingOrder = aggregate.order();
    ClientEntity savedClient = null;

    boolean clientChanged = false;
    boolean orderChanged = false;

    // 1. Process Client
    if (isMeaningfulClient(aggregate.client())) {
        List<ContactMethod> incomingContacts = aggregate.client().getContacts() != null
                ? aggregate.client().getContacts()
                : List.of();

        EmailContact existingEmail = incomingContacts.stream()
                .filter(cm -> cm instanceof EmailContact)
                .map(ec -> {
                    try {
                        return contactManagementService.getEmailContactByEmail(((EmailContact) ec).getPlainValue());
                    } catch (Exception e) {
                        throw new RuntimeException("Email lookup failed", e);
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);

        if (existingEmail != null && existingEmail.getClient() != null) {
            savedClient = existingEmail.getClient();
            clientChanged = syncClientData(aggregate.client(), savedClient);
        } else {
            savedClient = aggregate.client();
            clientChanged = true;
        }

        if (aggregate.contacts() != null) {
            final ClientEntity finalClient = savedClient;
            aggregate.contacts().forEach(c -> c.setClient(finalClient));
        }

        if (clientChanged || savedClient.getId() == null) {
            savedClient = clientRepository.save(savedClient);
        }
    }

    OrderEntity targetOrder = null;

    if (ctx.entityId() != null) {
        // entityId in enrichment request is an external order id (white admin id).
        targetOrder = orderRepository.findByWhiteAdminId(ctx.entityId()).orElse(null);
    }
    if (targetOrder == null && incomingOrder.getWhiteAdminId() != null) {
        targetOrder = orderRepository.findByWhiteAdminId(incomingOrder.getWhiteAdminId()).orElse(null);
    }

    if (targetOrder == null) {
        targetOrder = new OrderEntity();
        orderChanged = true;
    }

    if (aggregate.legalEntityInfo() != null) {
        try {
            String legalJson = objectMapper.writeValueAsString(aggregate.legalEntityInfo());
            if (!Objects.equals(targetOrder.getLegalEntityInfoJson(), legalJson)) {
                targetOrder.setLegalEntityInfoJson(legalJson);
                orderChanged = true;
            }
        } catch (Exception e) {
            log.error("Failed to serialize legal info", e);
        }
    }

    if (savedClient != null && (targetOrder.getClient() == null || !Objects.equals(targetOrder.getClient().getId(), savedClient.getId()))) {
        targetOrder.setClient(savedClient);
        orderChanged = true;
    }

    if (mergeOrder(targetOrder, incomingOrder)) {
        orderChanged = true;
    }

    if (targetOrder.getCreatedAt() == null) {
        targetOrder.setCreatedAt(Instant.now());
        orderChanged = true;
    }

    if (orderChanged || targetOrder.getId() == null) {
        targetOrder = orderRepository.save(targetOrder);
    }

    if (ensureEmailDeliveryTarget(targetOrder, savedClient, aggregate.contacts())) {
        targetOrder = orderRepository.save(targetOrder);
        orderChanged = true;
    }

    // 3. Process Order Items
    if (aggregate.items() != null) {
        for (OrderItemEntity item : aggregate.items()) {
            item.setOrderId(targetOrder.getId());
            if (item.getCount() == null || item.getCount() <= 0) {
                item.setCount(1);
            }
            if (item.getPcPerLicense() == null || item.getPcPerLicense() <= 0) {
                item.setPcPerLicense(1);
            }
            orderItemRepository.save(item);
        }
    }

    return new OrderUpsertResult(
            targetOrder.getId(),
            savedClient != null ? savedClient.getId() : null,
            orderChanged,
            clientChanged,
            false
    );
}

    private boolean ensureEmailDeliveryTarget(OrderEntity order, ClientEntity client, List<ContactMethod> aggregateContacts) {
        if (order == null) {
            return false;
        }
        ContactMethod emailContact = resolveEmailContact(client, aggregateContacts);
        if (emailContact == null) {
            return false;
        }

        if (order.getDeliveryTargets() == null) {
            order.setDeliveryTargets(new ArrayList<>());
        }

        for (OrderDeliveryTargetEntity existing : order.getDeliveryTargets()) {
            if (existing.getContactMethod() != null
                    && existing.getContactMethod().getId() != null
                    && existing.getContactMethod().getId().equals(emailContact.getId())) {
                boolean changed = false;
                if (!existing.isEnabled()) {
                    existing.setEnabled(true);
                    changed = true;
                }
                if (existing.getOutputFormat() == null) {
                    existing.setOutputFormat(OutputType.TEXT);
                    changed = true;
                }
                return changed;
            }
        }

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setOrder(order);
        target.setContactMethod(emailContact);
        target.setOutputFormat(OutputType.TEXT);
        target.setEnabled(true);
        order.getDeliveryTargets().add(target);
        return true;
    }

    private ContactMethod resolveEmailContact(ClientEntity savedClient, List<ContactMethod> aggregateContacts) {
        if (savedClient != null && savedClient.getContacts() != null) {
            for (ContactMethod contact : savedClient.getContacts()) {
                if (contact instanceof EmailContact) {
                    return contact;
                }
            }
        }
        if (aggregateContacts != null) {
            for (ContactMethod contact : aggregateContacts) {
                if (contact instanceof EmailContact emailContact) {
                    try {
                        Optional<EmailContact> existing = contactManagementService.getEmailContactByEmail(emailContact.getPlainValue());
                        if (existing.isPresent()) {
                            return existing.get();
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to resolve email contact for delivery target: {}", ex.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private boolean syncClientData(ClientEntity source, ClientEntity target) {
        boolean changed = false;
        if (target.getContacts() == null) {
            target.setContacts(new ArrayList<>());
        }

        if ((target.getName() == null || target.getName().isBlank()) && source.getName() != null && !source.getName().isBlank()) {
            target.setName(source.getName());
            changed = true;
        }
        if ((target.getPhone() == null || target.getPhone().isBlank()) && source.getPhone() != null && !source.getPhone().isBlank()) {
            target.setPhone(source.getPhone());
            changed = true;
        }

        if (source.getContacts() != null) {
            for (ContactMethod newContact : source.getContacts()) {
                boolean exists = target.getContacts().stream().anyMatch(c -> isSameContact(c, newContact));
                if (!exists) {
                    newContact.setClient(target);
                    target.getContacts().add(newContact);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean isSameContact(ContactMethod c1, ContactMethod c2) {
        if (!c1.getClass().equals(c2.getClass())) return false;
        if (c1 instanceof EmailContact) return true;
        if (c1 instanceof IpContact) {
            String val1 = ((IpContact) c1).getPlainValue();
            String val2 = ((IpContact) c2).getPlainValue();
            if (val1 == null || val2 == null) return false;
            return val1.trim().equals(val2.trim());
        }
        return false;
    }

    private boolean isMeaningfulClient(ClientEntity client) {
        if (client == null) {
            return false;
        }
        if (client.getName() != null && !client.getName().isBlank()) {
            return true;
        }
        if (client.getPhone() != null && !client.getPhone().isBlank()) {
            return true;
        }
        if (client.getContacts() == null || client.getContacts().isEmpty()) {
            return false;
        }
        return client.getContacts().stream().anyMatch(this::isMeaningfulContact);
    }

    private boolean isMeaningfulContact(ContactMethod contact) {
        if (contact instanceof EmailContact emailContact) {
            String plain = emailContact.getPlainValue();
            String encrypted = emailContact.getEncryptedValue();
            return (plain != null && !plain.trim().isEmpty())
                    || (encrypted != null && !encrypted.trim().isEmpty());
        }
        if (contact instanceof IpContact ipContact) {
            String ip = ipContact.getPlainValue();
            return ip != null && !ip.trim().isEmpty();
        }
        return false;
    }



    private boolean mergeOrder(OrderEntity target, OrderEntity incoming) {
        boolean changed = false;

        if (incoming.getPortalId() != null && !Objects.equals(target.getPortalId(), incoming.getPortalId())) {
            target.setPortalId(incoming.getPortalId());
            changed = true;
        }
        if (incoming.getWhiteAdminId() != null && !Objects.equals(target.getWhiteAdminId(), incoming.getWhiteAdminId())) {
            target.setWhiteAdminId(incoming.getWhiteAdminId());
            changed = true;
        }
        if (incoming.getOrderStatus() != null && !Objects.equals(target.getOrderStatus(), incoming.getOrderStatus())) {
            target.setOrderStatus(incoming.getOrderStatus());
            changed = true;
        }
        if (incoming.getPaymentMethod() != null && !Objects.equals(target.getPaymentMethod(), incoming.getPaymentMethod())) {
            target.setPaymentMethod(incoming.getPaymentMethod());
            changed = true;
        }
        if (incoming.getTotalAmount() != null && !Objects.equals(target.getTotalAmount(), incoming.getTotalAmount())) {
            target.setTotalAmount(incoming.getTotalAmount());
            changed = true;
        }
        if (incoming.getCurrency() != null && !Objects.equals(target.getCurrency(), incoming.getCurrency())) {
            target.setCurrency(incoming.getCurrency());
            changed = true;
        }
        if (incoming.getCreatedAtOrigin() != null && !Objects.equals(target.getCreatedAtOrigin(), incoming.getCreatedAtOrigin())) {
            target.setCreatedAtOrigin(incoming.getCreatedAtOrigin());
            changed = true;
        }
        if (incoming.getUpdatedAtAtOrigin() != null && !Objects.equals(target.getUpdatedAtAtOrigin(), incoming.getUpdatedAtAtOrigin())) {
            target.setUpdatedAtAtOrigin(incoming.getUpdatedAtAtOrigin());
            changed = true;
        }
        if (incoming.getCreatedAt() != null && !Objects.equals(target.getCreatedAt(), incoming.getCreatedAt())) {
            target.setCreatedAt(incoming.getCreatedAt());
            changed = true;
        }
        if (incoming.getUserComment() != null && !Objects.equals(target.getUserComment(), incoming.getUserComment())) {
            target.setUserComment(incoming.getUserComment());
            changed = true;
        }
        if (incoming.getClientComment() != null && !Objects.equals(target.getClientComment(), incoming.getClientComment())) {
            target.setClientComment(incoming.getClientComment());
            changed = true;
        }
        if (incoming.getExternalRef() != null && !Objects.equals(target.getExternalRef(), incoming.getExternalRef())) {
            target.setExternalRef(incoming.getExternalRef());
            changed = true;
        }
        if (incoming.getHttpRef() != null && !Objects.equals(target.getHttpRef(), incoming.getHttpRef())) {
            target.setHttpRef(incoming.getHttpRef());
            changed = true;
        }
        if (incoming.getClientType() != null && !Objects.equals(target.getClientType(), incoming.getClientType())) {
            target.setClientType(incoming.getClientType());
            changed = true;
        }
        if (incoming.getLegalEntityInfoJson() != null && !Objects.equals(target.getLegalEntityInfoJson(), incoming.getLegalEntityInfoJson())) {
            target.setLegalEntityInfoJson(incoming.getLegalEntityInfoJson());
            changed = true;
        }
        return changed;
    }

}
