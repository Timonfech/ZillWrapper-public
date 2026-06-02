package com.zillya.timonfech.zillwrapper.core.services;

import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderSourceContextEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.repos.ClientRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.DeliveryTargetSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.services.persistance.ContactManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ClientRepository clientRepository;
    private final ContactManagementService contactManagementService;

    @Transactional
    public Long createOrder(OrderOperationContext context) {
        OrderEntity savedOrder = findExistingOrderByRefs(context).orElseGet(() -> {
            OrderEntity order = new OrderEntity();
            order.setPortalId(context.getPortalId());
            order.setWhiteAdminId(context.getWhiteAdminId());
            order.setUserComment(context.getUserComment());
            order.setCreatedAt(Instant.now());
            order.setOrderStatus(context.isPayedReady() ? OrderStatus.PAYED : OrderStatus.NEW);
            order.setClient(resolveOrCreateClientByEmail(context.primaryEmail(), context.getLocaleTag(), context.getPartnerOverride()));
            return orderRepository.save(order);
        });

        // For repeated order submissions with same business reference we append items to existing order.
        if (savedOrder.getClient() == null) {
            savedOrder.setClient(resolveOrCreateClientByEmail(context.primaryEmail(), context.getLocaleTag(), context.getPartnerOverride()));
        } else if (context.getPartnerOverride() != null && savedOrder.getClient().getClientType() != expectedClientType(context.getPartnerOverride())) {
            savedOrder.getClient().setClientType(expectedClientType(context.getPartnerOverride()));
            savedOrder.setClient(clientRepository.save(savedOrder.getClient()));
        }
        if (savedOrder.getWhiteAdminId() == null && context.getWhiteAdminId() != null) {
            savedOrder.setWhiteAdminId(context.getWhiteAdminId());
        }
        if (savedOrder.getPortalId() == null && context.getPortalId() != null) {
            savedOrder.setPortalId(context.getPortalId());
        }
        if ((savedOrder.getUserComment() == null || savedOrder.getUserComment().isBlank())
                && context.getUserComment() != null
                && !context.getUserComment().isBlank()) {
            savedOrder.setUserComment(context.getUserComment());
        }
        if (context.isPayedReady() && savedOrder.getOrderStatus() != OrderStatus.PAYED) {
            savedOrder.setOrderStatus(OrderStatus.PAYED);
        }
        if (savedOrder.getItems() == null) {
            savedOrder.setItems(new ArrayList<>());
        }
        if (savedOrder.getDeliveryTargets() == null) {
            savedOrder.setDeliveryTargets(new ArrayList<>());
        }
        appendSourceContext(savedOrder, context);
        
        for (OrderItemSpec spec : context.getItemSpecs()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(savedOrder.getId());
            item.setOrder(savedOrder);
            item.setProductId(spec.product().productId());
            item.setProductBrandId(spec.product().brandId());
            item.setCount(spec.count());
            item.setBusinessPeriod(spec.period());
            item.setPcPerLicense(spec.computers());
            item.setOutputTypes(spec.outputTypes());
            item.setKeyTypes(spec.keyTypes());
            item.setServerNumber(spec.options() == null ? null : spec.options().serverNumber());
            item.setProcessingStatus(ItemProcessingStatus.PENDING);
            
            orderItemRepository.save(item);
            savedOrder.getItems().add(item);
            log.info("Order item prepared orderId={} itemId={} product={}/{} keyTypes={} outputTypes={} count={}",
                    savedOrder.getId(),
                    item.getId(),
                    item.getProductBrandId(),
                    item.getProductId(),
                    item.getKeyTypes(),
                    item.getOutputTypes(),
                    item.getCount());
        }

        // Persist delivery targets for notify stage.
        for (DeliveryTargetSpec spec : context.getDeliveryTargets()) {
            ContactMethod savedContact = saveDeliveryContact(spec, savedOrder.getClient());
            if (savedContact == null) {
                continue;
            }
            boolean alreadyPresent = savedOrder.getDeliveryTargets().stream()
                    .anyMatch(existing -> existing.isEnabled()
                            && existing.getOutputFormat() == spec.format()
                            && existing.getContactMethod() != null
                            && existing.getContactMethod().getId() != null
                            && existing.getContactMethod().getId().equals(savedContact.getId()));
            if (alreadyPresent) {
                continue;
            }

            OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
            target.setOrder(savedOrder);
            target.setContactMethod(savedContact);
            target.setOutputFormat(spec.format());
            target.setEnabled(true);
            savedOrder.getDeliveryTargets().add(target);
        }
        
        orderRepository.save(savedOrder);
        
        log.info("Created Order {} with {} items and {} delivery targets",
                savedOrder.getId(),
                savedOrder.getItems().size(),
                savedOrder.getDeliveryTargets().size());
        return savedOrder.getId();
    }

    private void appendSourceContext(OrderEntity order, OrderOperationContext context) {
        if (order == null || order.getId() == null || context == null || context.getSourceId() == null) {
            return;
        }
        if (order.getSourceContexts() == null) {
            order.setSourceContexts(new ArrayList<>());
        }
        OrderSourceContextEntity sourceContext = new OrderSourceContextEntity();
        sourceContext.setOrder(order);
        sourceContext.setSourceId(context.getSourceId());
        sourceContext.setOperationType(context.getCurrentStage() == null ? OperationType.ORDER_CREATION : context.getCurrentStage());
        sourceContext.setOperationId(context.getOperationId());
        sourceContext.setUserId(context.getInitiatorUserId());
        sourceContext.setCapturedAt(Instant.now());
        sourceContext.setContextData("{}");
        order.getSourceContexts().add(sourceContext);
    }

    private Optional<OrderEntity> findExistingOrderByRefs(OrderOperationContext context) {
        if (context.getWhiteAdminId() != null) {
            Optional<OrderEntity> byWa = orderRepository.findByWhiteAdminId(context.getWhiteAdminId());
            if (byWa.isPresent()) {
                return byWa;
            }
        }
        if (context.getPortalId() != null) {
            return orderRepository.findByPortalId(context.getPortalId());
        }
        return Optional.empty();
    }

    private ContactMethod saveDeliveryContact(DeliveryTargetSpec spec, ClientEntity client) {
        if (spec.type() == ContactMethodType.EMAIL) {
            Optional<EmailContact> existing = findEmail(spec.value());
            if (existing.isPresent()) {
                return existing.get();
            }
            EmailContact emailContact = new EmailContact(spec.value());
            emailContact.setType(ContactMethodType.EMAIL);
//            emailContact.setLabel("Order delivery e-mail");
            emailContact.setClient(client);
            return contactManagementService.saveContact(emailContact);
        }

        log.warn("Unsupported delivery contact type: {}", spec.type());
        return null;
    }

    private ClientEntity resolveOrCreateClientByEmail(String email, String localeTag, Boolean partnerOverride) {
        Optional<EmailContact> existingEmail = findEmail(email);
        if (existingEmail.isPresent() && existingEmail.get().getClient() != null) {
            ClientEntity existingClient = existingEmail.get().getClient();
            if (existingClient.getLocale() == null && localeTag != null && !localeTag.isBlank()) {
                existingClient.setLocale(Locale.forLanguageTag(localeTag));
            }
            if (partnerOverride != null && existingClient.getClientType() != expectedClientType(partnerOverride)) {
                existingClient.setClientType(expectedClientType(partnerOverride));
            }
            return clientRepository.save(existingClient);
        }

        ClientEntity client = new ClientEntity();
        client.setLocale((localeTag == null || localeTag.isBlank()) ? Locale.forLanguageTag("uk") : Locale.forLanguageTag(localeTag));
        if (partnerOverride != null) {
            client.setClientType(expectedClientType(partnerOverride));
        }
        client.setContacts(new ArrayList<>());
        client = clientRepository.save(client);

        if (existingEmail.isPresent()) {
            EmailContact orphan = existingEmail.get();
            orphan.setClient(client);
            EmailContact linked = (EmailContact) contactManagementService.saveContact(orphan);
            client.getContacts().add(linked);
            return clientRepository.save(client);
        }

        if (email != null && !email.isBlank()) {
            EmailContact contact = new EmailContact(email);
            contact.setType(ContactMethodType.EMAIL);
            contact.setLabel("Client e-mail");
            contact.setClient(client);
            EmailContact saved = (EmailContact) contactManagementService.saveContact(contact);
            client.getContacts().add(saved);
            client = clientRepository.save(client);
        }
        return client;
    }

    private ClientType expectedClientType(boolean partnerOverride) {
        return partnerOverride ? ClientType.PARTNER : ClientType.STANDARD;
    }

    private Optional<EmailContact> findEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try {
            return contactManagementService.getEmailContactByEmail(email);
        } catch (Exception ex) {
            log.warn("Email lookup failed for {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }
}
