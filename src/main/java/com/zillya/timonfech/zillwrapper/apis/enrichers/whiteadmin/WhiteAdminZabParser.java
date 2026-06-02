package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentActivationRuntimeService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.LicenseDedupService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.LicenseExternalIdResolver;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LicenseAggregate;
import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderParseException;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderReferenceLineParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderReference;
import com.zillya.timonfech.zillwrapper.core.regex.MatchingException;
import com.zillya.timonfech.zillwrapper.core.regex.NaturalDurationMatcher;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Robust Parser for WhiteAdmin (Zab Edition)
 */
@Service
@Slf4j
public class WhiteAdminZabParser extends AbstractWhiteAdminLicenseEnrich {

    private final String latestUrl;
    private final String detailsUrl;
    private final OrderReferenceLineParser orderReferenceLineParser;

    public WhiteAdminZabParser(
            @Value("${whiteAdminPanel.zab.latestPage}") String latestUrl,
            @Value("${whiteAdminPanel.zab.detailsPage}") String detailsUrl,
            AbstractWhiteAdminClient client,
            LicenseExternalIdResolver externalIdResolver,
            LicenseDedupService dedupService,
            EnrichmentProgressRegistry progressRegistry,
            ApplicationEventPublisher publisher,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
            LicenseRepository licenseRepository,
            EnrichmentActivationRuntimeService activationRuntimeService,
            OrderReferenceLineParser orderReferenceLineParser) {
        super(publisher, progressRegistry, enrichmentTaskExecutor, client, externalIdResolver, dedupService, licenseRepository, activationRuntimeService);
        this.latestUrl = latestUrl;
        this.detailsUrl = detailsUrl;
        this.orderReferenceLineParser = orderReferenceLineParser;

    }

    @Override
    protected Integer brandId() {
        return 2;
    }

    @Override
    protected Integer productId() {
        return 4;
    }

    @Override
    protected String targetUrl() {
        return detailsUrl;
    }

    public long fetchLatestId() {
        try {
            Document doc = this.client.loadDocument(this.latestUrl, null, false);

            Elements paymentRows = doc.select("tr.payment_row");

            if (paymentRows.isEmpty()) {
                throw new RuntimeException("CHECK ZIS 2.0 web page - less than 2 payment rows");
            }

            Element latestRow = paymentRows.get(0);
            Element targetTd = latestRow.selectFirst("td:nth-child(1)");
            try {
                assert targetTd != null;
                String id = targetTd.text().trim();
                return Long.parseLong(id);
            } catch (NumberFormatException e) {
                throw new RuntimeException("CHECK ZAB web page - invalid ID td format" , e);
            }

        } catch (IOException e) {
            throw new RuntimeException("CHECK ZAB web page - IO error", e);
        }
    }


    @Override
    protected LicenseAggregate parse(Document doc, Long externalId) {
        Optional<String> createdAtRawOpt = extractRowValue(doc, "Created:");
        if (isDeletedByCreatedAtMarker(createdAtRawOpt)) {
            throw new IllegalStateException("SKIP_DELETED_LICENSE");
        }

        String onKey = extractLegendValue(doc, "License properties: ")
                .orElseThrow(() -> new IllegalStateException("Required field not found: activation code"));

        String createdAtRaw = createdAtRawOpt
                .orElseThrow(() -> new IllegalStateException("Required field not found: createdAt"));

        String periodRaw = extractRowValue(doc, "Term:").orElse("");
        Optional<String> expiresAtRaw = resolveExpiresAt(doc);
        String username = inputValueById(doc, "user_name").orElse("");
        String email = inputValueById(doc, "user_mail").orElse("");
        String company = inputValueById(doc, "company").orElse("");
        String reservedDevices = inputValueById(doc, "reserved").orElse("0");
        String reservedServers = inputValueById(doc, "server_number").orElse("1");

        Optional<String> statusRaw = resolveCurrentSelectValueById(doc, "status");

        String comments = textareaValueById(doc, "cmt").orElse("");
        String offKey = extractTextareaByLegend(doc, "License key")
                .orElseThrow(() -> new IllegalStateException("Required field not found: license key"));

        LicenseEntity license = new LicenseEntity();
        license.setExternalId(externalId);
        license.setBrandId(brandId());
        license.setProductId(productId());
        license.setDescription(comments);

        NaturalDurationMatcher ndm = new NaturalDurationMatcher();
        try {
            Optional<BusinessPeriod> match = ndm.match(periodRaw);
            match.ifPresent(license::setBusinessPeriod);
        } catch (MatchingException e) {
            throw new RuntimeException(e);
        }


        try {
            int resDevices = Integer.parseInt(reservedDevices);
            license.setDevices(resDevices);
        } catch (NumberFormatException e) {
            log.warn(String.valueOf(e));
        }

        statusRaw.ifPresentOrElse(raw -> {
            switch (raw) {
                case "0" -> license.setStatus(LicenseStatus.ALLOWED);
                case "1" -> license.setStatus(LicenseStatus.BLOCKED);
                case "2" -> license.setStatus(LicenseStatus.BLOCKED_NEW);
                case "3" -> license.setStatus(LicenseStatus.BLOCKED_OVER);
                default -> log.warn("Unrecognized ZAB statusRaw='{}' for externalId={}", raw, externalId);
            }
        }, () -> log.warn("Missing ZAB status for externalId={}", externalId));

        WhiteAdminKeyEntity whiteadminKey = new WhiteAdminKeyEntity();
        whiteadminKey.setOnlineKey(onKey);
        whiteadminKey.setOfflineKey(KeyMarkersUtils.removeMarkers(offKey));
        whiteadminKey.setCompany(company);
        try {
            int resServs = Integer.parseInt(reservedServers);
            whiteadminKey.setReservedServers(resServs);
        } catch (NumberFormatException e) {
            log.warn(String.valueOf(e));
        }
        license.setKey(whiteadminKey);

       parseToInstant(createdAtRaw).ifPresent(license::setCreatedAtOrigin);
       expiresAtRaw.ifPresent(raw -> parseToInstant(raw).ifPresent(license::setExpiresAt));


        ClientEntity client = null;
        if ((username != null && !username.isBlank()) || (email != null && !email.isBlank())) {
            client = new ClientEntity();
            if (username != null && !username.isBlank()) {
                client.setName(username);
            }
            if (email != null && !email.isBlank()) {
                ContactMethod contactMethod = new EmailContact(email);
                contactMethod.setClient(client);
                contactMethod.setType(ContactMethodType.EMAIL);
                contactMethod.setLabel("admin_email");
            }
        }

        OrderEntity order = null;
        if (!comments.isBlank() && comments.chars().allMatch(Character::isDigit)) {
            try {
                ParsedOrderReference parsed = orderReferenceLineParser.parse(comments.trim());
                if (parsed.portalId() != null || parsed.whiteAdminId() != null) {
                    order = new OrderEntity();
                    order.setPortalId(parsed.portalId());
                    order.setWhiteAdminId(parsed.whiteAdminId());
                    if (client != null) {
                        order.setClient(client);
                    }
                }
            } catch (OrderParseException ex) {
                log.debug("Failed to classify WA order reference from ZAB comment='{}': {}", comments, ex.getMessage());
            }
        }

        return new LicenseAggregate(license, client, order);
    }

    @Override
    public String name() {
        return "ZAB_ENRICHER";
    }
    private Optional<String> resolveCurrentSelectValueById(Document doc, String id) {
        Element select = doc.getElementById(id);
        if (select == null) {
            return Optional.empty();
        }
        if (select.hasAttr("value")) {
            return clean(select.attr("value"));
        }
        Element selected = select.selectFirst("option[selected]");
        if (selected != null) {
            return clean(selected.attr("value"));
        }
        return Optional.empty();
    }

    private Optional<String> resolveExpiresAt(Document doc) {
        Optional<String> byId = inputValueById(doc, "expired");
        if (byId.isPresent()) {
            return byId;
        }
        return extractRowValue(doc, "Expired:");
    }
}
