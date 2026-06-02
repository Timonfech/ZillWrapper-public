package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
import com.zillya.timonfech.zillwrapper.apis.enrichers.OrderDedupService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LegalEntityInfo;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.OrderAggregate;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.PaymentMethod;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.CurrencyCode;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.IpContact;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.regex.MatchingException;
import com.zillya.timonfech.zillwrapper.core.regex.NaturalDurationMatcher;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class WhiteAdminOrdersParser extends AbstractWhiteAdminOrderEnrich {

    private final String listPageUrl;
    private final String detailsPageUrl;
    private final AbstractWhiteAdminClient client;
    private final ProductRegistry productRegistry;
    private final WhiteAdminPayedOrderIdsProvider payedOrderIdsProvider;


    protected WhiteAdminOrdersParser(@Value("${whiteAdminPanel.orders.listPage}") String listPageUrl,
                                     @Value("${whiteAdminPanel.orders.detailsPage}") String detailsPageUrl,
                                     AbstractWhiteAdminClient client,
                                     OrderExternalIdResolver externalIdResolver,
                                     OrderDedupService dedupService,
                                     EnrichmentProgressRegistry progressRegistry,
                                     ApplicationEventPublisher publisher,
                                     @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
                                     ProductRegistry productRegistry,
                                     WhiteAdminPayedOrderIdsProvider payedOrderIdsProvider

    ) {
        super(publisher, progressRegistry, enrichmentTaskExecutor, client, externalIdResolver, dedupService);
        this.listPageUrl = listPageUrl;
        this.detailsPageUrl = detailsPageUrl;
        this.client = client;
        this.productRegistry = productRegistry;
        this.payedOrderIdsProvider = payedOrderIdsProvider;

    }
/*
* @return COULD BE list of -1 in case of exception or empty if there are no new payed orders
* */
    protected List<Long> getPayedIds() {
        return payedOrderIdsProvider.getPayedIds();
    }


    @Override
    public long fetchLatestId() {
        try {
            Document doc = this.client.loadDocument(this.listPageUrl, null, false);

            Element targetTd = doc.selectFirst("table tbody tr.payment_row td");

            if (targetTd == null) {
                throw new RuntimeException("CHECK order web page - first payment order not found");
            }

            try {
                String id = targetTd.text().trim();
                return Long.parseLong(id);
            } catch (NumberFormatException e) {
                throw new RuntimeException("CHECK order web page - first payment order has invalid ID format ", e);
            }

        } catch (IOException e) {
            throw new RuntimeException("CHECK order web page - IO error while fetching first payment order", e);
        }
    }

    public List<Long> fetchLastOrderIds(int limit) {
        int safeLimit = Math.max(1, limit);
        try {
            Document doc = this.client.loadDocument(this.listPageUrl, null, false);
            Elements rows = doc.select("table tbody tr.payment_row");
            List<Long> ids = new ArrayList<>();
            for (Element row : rows) {
                Element idCell = row.selectFirst("td");
                if (idCell == null) {
                    continue;
                }
                String raw = idCell.text().trim();
                if (raw.chars().allMatch(Character::isDigit)) {
                    ids.add(Long.parseLong(raw));
                    if (ids.size() >= safeLimit) {
                        break;
                    }
                }
            }
            return ids;
        } catch (IOException e) {
            throw new RuntimeException("CHECK order web page - IO error while fetching latest order ids", e);
        }
    }

    public OrderMatchCard loadOrderMatchCard(Long externalId) {
        List<org.apache.http.NameValuePair> params = List.of(
                new org.apache.http.message.BasicNameValuePair("id", String.valueOf(externalId))
        );
        try {
            Document doc = client.loadDocument(detailsPageUrl, params, false);
            Elements values = doc.select("html body fieldset table tbody tr td");
            if (values.size() < 18) {
                throw new IllegalStateException("SKIP_DELETED_ORDER: insufficient fields size=" + values.size());
            }
            String clientName = values.get(13).text().trim();
            String email = values.get(14).text().trim();
            String phone = values.get(15).text().trim();
            String clientComment = values.get(17).text().trim();
            String legalAddress = values.size() > 21 ? values.get(21).text().trim() : "";
            return new OrderMatchCard(clientName, email, phone, legalAddress, clientComment);
        } catch (IOException e) {
            throw new RuntimeException("CHECK order web page - IO error while loading order details for id=" + externalId, e);
        }
    }


    protected OrderAggregate parse(Document doc, Long externalId) {

        String productNameRaw = "";
        String costRaw = "";
        String amount = "";
        String termRaw = ""; // with 'мес'
        String statusRAW = "";
        String paymentMethod = "";
        String ref = "";
        String httpRef = "";
        String addDateRaw = "";
        String modifiedDateRaw = "";
//        String payedDateRaw = "";

        // ---- client data ----
        String clientType = "";
        String clientName = "";
        String email = "";
        String clientPhone = "";
        String clientIp = "";
        String clientComment = "";

        // ========== LEGAL ENTITY ==========
        String TIN = "";
        String companyName = "";
        String physicalAddress = "";
        String registeredAddress = "";

//html body fieldset table
        Elements values = doc.select("html body fieldset table tbody tr td");
        if (values.size() < 18) {
            throw new IllegalStateException("SKIP_DELETED_ORDER: insufficient fields size=" + values.size());
        }


        productNameRaw = values.get(0).text().trim();
        costRaw = values.get(1).text().trim();
        amount = values.get(2).text().trim();
        termRaw = values.get(3).text().trim();
        statusRAW = values.get(4).text().trim();
        paymentMethod = values.get(5).text().trim();
        ref = values.get(6).text().trim();
        httpRef = values.get(7).text().trim();
        addDateRaw = values.get(8).text().trim();
        modifiedDateRaw = values.get(9).text().trim();
//        payedDateRaw = values.get(10).text().trim();

        if (productNameRaw.isBlank() && costRaw.isBlank() && ref.isBlank() && addDateRaw.isBlank()) {
            throw new IllegalStateException("SKIP_DELETED_ORDER: empty core fields");
        }

// ---- client data ----
        clientType = values.get(12).text().trim();
        clientName = values.get(13).text().trim();
        email = values.get(14).text().trim();
        clientPhone = values.get(15).text().trim();
        clientIp = values.get(16).text().trim();
        clientComment = values.get(17).text().trim();

// ========== LEGAL ENTITY ==========
        LegalEntityInfo legalInfo = null;
        if (doc.select("html body fieldset legend").getFirst().text().equals("Данные о юр. лице")) {
            TIN = values.get(18).text().trim();
            companyName = values.get(19).text().trim();
            physicalAddress = values.get(20).text().trim();
            registeredAddress = values.get(21).text().trim();


            legalInfo = new LegalEntityInfo(
                    TIN,
                    companyName,
                    physicalAddress,
                    registeredAddress
            );


        }

        OrderEntity order = new OrderEntity();
        order.setWhiteAdminId(externalId);

        ClientEntity client = null;

        // +++++ COMMENT ++++++
        Element adminCommentEl = doc.selectFirst("fieldset textarea#admin_comment");
        String adminComment = adminCommentEl == null ? "" : adminCommentEl.val();

// ----  ClientEntity ----
        List<ContactMethod> contacts = new ArrayList<>(1);
        boolean hasClientData = !clientName.isEmpty() || !clientPhone.isEmpty() || !email.isEmpty() || !clientIp.isEmpty();
        if (hasClientData) {
            client = new ClientEntity();
            if (!clientName.isEmpty()) {
                client.setName(clientName);
            }
            if (!clientPhone.isEmpty()) {
                client.setPhone(clientPhone);
            }
            if (!email.isEmpty()) {
                EmailContact emailContact = new EmailContact(email);
                emailContact.setClient(client);
                contacts.add(emailContact);
                client.setContacts(contacts);
            }
            if (!clientIp.isEmpty()) {
                IpContact ipContact = new IpContact(clientIp);
                ipContact.setClient(client);
                contacts.add(ipContact);
            }
            client.setContacts(contacts);
            order.setClient(client);
        }
        mapOrderStatus(statusRAW).ifPresent(order::setOrderStatus);

        String[] s = costRaw.split(" ");
        order.setTotalAmount(BigDecimal.valueOf(Float.parseFloat(s[0])));
        order.setCurrency(CurrencyCode.valueOf(s[1].toUpperCase()));

        if (paymentMethod.equalsIgnoreCase("PORTMONE")) {
            order.setPaymentMethod(PaymentMethod.PORTMONE);
        }
        if (paymentMethod.equalsIgnoreCase("КВИТАНЦІЯ")) {
            order.setPaymentMethod(PaymentMethod.IBAN);
        }
        if (paymentMethod.equalsIgnoreCase("Счет-фактура")) {
            order.setPaymentMethod(PaymentMethod.INVOICE);
        }
        order.setClientComment(clientComment);

        parseToInstant(addDateRaw.trim()).ifPresent(order::setCreatedAtOrigin);
        parseToInstant(modifiedDateRaw.trim()).ifPresent(order::setUpdatedAtAtOrigin);

        if (!adminComment.isEmpty()) {
            order.setUserComment(adminComment);
        }

        if (!ref.isEmpty()) {
            order.setExternalRef(ref);
        }
        if (!httpRef.isEmpty()) {
            order.setHttpRef(httpRef);
        }
        if (!clientType.isEmpty()) {
            order.setClientType(clientType);
        }

// ----  OrderItemEntity ----

        OrderItemEntity item = new OrderItemEntity();
        ProductInfo productInfo = resolveProductInfo(productNameRaw, externalId);


        item.setCount(1);
        item.setPcPerLicense(Integer.parseInt(amount));
        item.setProductId(productInfo.productId());
        item.setProductBrandId(productInfo.brandId());
        // TODO: derive requested key type from explicit user markers in WA comments/payload
        // (e.g. offline marker) once the marker policy is finalized.
        item.setKeyTypes(List.of(KeyType.ONLINE));


        NaturalDurationMatcher naturalDurationMatcher = new NaturalDurationMatcher();
        try {
            Optional<BusinessPeriod> match = naturalDurationMatcher.match(termRaw);
            match.ifPresent(item::setBusinessPeriod);
        } catch (MatchingException e) {
            log.warn(String.valueOf(e));
        }
        order.getItems().add(item);


// 2.
        return new OrderAggregate(
                order,
                client,
                contacts,
                order.getItems(),
                legalInfo
        );
    }

    @Override
    protected String targetUrl() {
        return this.detailsPageUrl;
    }



    @Override
    public String name() {
        return "WHITE_ADMIN_ORDER_ENRICHER";
    }

    private ProductInfo resolveProductInfo(String rawProductName, Long externalId) {
        Optional<ProductInfo> aliasMatch = resolveAliasProduct(rawProductName);
        if (aliasMatch.isPresent()) {
            return aliasMatch.get();
        }

        Optional<ProductInfo> exact = productRegistry.findProductByText(rawProductName);
        if (exact.isPresent()) {
            return exact.get();
        }

        String normalized = normalizeProductText(rawProductName);
        Optional<ProductInfo> normalizedMatch = productRegistry.getAllProducts().stream()
                .filter(p -> p.matches(normalized))
                .findFirst();
        if (normalizedMatch.isPresent()) {
            return normalizedMatch.get();
        }

        Optional<ProductInfo> byNameContains = productRegistry.getAllProducts().stream()
                .filter(p -> p.names() != null)
                .filter(p -> p.names().values().stream().anyMatch(n ->
                        normalizeProductText(n).contains(normalized) || normalized.contains(normalizeProductText(n))
                ))
                .findFirst();
        if (byNameContains.isPresent()) {
            return byNameContains.get();
        }

        String available = productRegistry.getAllProducts().stream()
                .map(p -> p.brandId() + "/" + p.productId() + ":" + p.names().values().stream().findFirst().orElse("<no-name>"))
                .limit(8)
                .collect(Collectors.joining(", "));
        throw new IllegalStateException("Unknown product from WhiteAdmin orderId=" + externalId
                + ", rawProductName='" + rawProductName + "', normalized='" + normalized
                + "'. Available sample: " + available);
    }

    private Optional<ProductInfo> resolveAliasProduct(String rawProductName) {
        String normalized = normalizeProductText(rawProductName).toLowerCase();
        boolean looksLikeZis2 = normalized.contains("internet security")
                && (normalized.contains("2.0") || normalized.contains("версія 2") || normalized.contains("versia 2"));
        if (looksLikeZis2) {
            return productRegistry.getAllProducts().stream()
                    .filter(p -> p.brandId() == 2 && p.productId() == 3)
                    .findFirst();
        }
        return Optional.empty();
    }

    private String normalizeProductText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace("?", "")
                .replace("!", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Optional<OrderStatus> mapOrderStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OrderStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown WhiteAdmin order status '{}', will not update orderStatus", rawStatus);
            return Optional.empty();
        }
    }

    public record OrderMatchCard(
            String clientName,
            String email,
            String phone,
            String docAddress,
            String clientComment
    ) {
    }


}
