package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentActivationRuntimeService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
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
import com.zillya.timonfech.zillwrapper.core.regex.MatchingException;
import com.zillya.timonfech.zillwrapper.core.regex.NaturalDurationMatcher;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderParseException;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderReferenceLineParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderReference;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class WhiteAdminZis2Parser extends AbstractWhiteAdminLicenseEnrich {

    private final String targetUrl;
    private final String licenseUrl;
    private final OrderReferenceLineParser orderReferenceLineParser;

    public WhiteAdminZis2Parser(
            @Value("${whiteAdminPanel.zis2.detailsPage}") String targetUrl,
            @Value("${whiteAdminPanel.zis2.latestPage}") String licenseUrl,
            AbstractWhiteAdminClient client,
            LicenseExternalIdResolver externalIdResolver,
            LicenseDedupService dedupService,
            EnrichmentProgressRegistry progressRegistry,
            ApplicationEventPublisher publisher,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
            LicenseRepository licenseRepo,
            EnrichmentActivationRuntimeService activationRuntimeService,
            OrderReferenceLineParser orderReferenceLineParser) {
        super(publisher, progressRegistry, enrichmentTaskExecutor, client, externalIdResolver, dedupService, licenseRepo, activationRuntimeService);
        this.targetUrl = targetUrl;
        this.licenseUrl = licenseUrl;
        this.orderReferenceLineParser = orderReferenceLineParser;
    }

    @Override
    protected Integer brandId() {
        return 2;
    }

    @Override
    protected Integer productId() {
        return 3;
    }

    @Override
    protected String targetUrl() {
        return targetUrl;
    }

    @Override
    public long fetchLatestId() {
        try {
            Document doc = this.client.loadDocument(this.licenseUrl, null, false);
            Elements tables = doc.select("body > table");
            if (tables.size() < 2) {
                throw new RuntimeException("CHECK ZIS 2.0 web page ");
            }
            Element table2 = tables.get(1);
            Elements paymentRows = table2.select("tr.payment_row");
            if (paymentRows.isEmpty()) {
                throw new RuntimeException("CHECK ZIS 2.0 web page ");
            }
            Element latestRow = paymentRows.get(0);
            Element firstTd = latestRow.selectFirst("td");
            if (firstTd == null) {
                throw new RuntimeException("CHECK ZIS 2.0 web page ");
            }
            return Long.parseLong(firstTd.text());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected LicenseAggregate parse(Document doc, Long externalId) {
        Optional<String> createdAtRawOpt = resolveCreatedAt(doc);
        if (isDeletedByCreatedAtMarker(createdAtRawOpt)) {
            throw new IllegalStateException("SKIP_DELETED_LICENSE");
        }
        String onKey = resolveActivationCode(doc)
                .orElseThrow(() -> new IllegalStateException("Required field not found: activation code"));

        String createdAtRaw = createdAtRawOpt
                .orElseThrow(() -> new IllegalStateException("Required field not found: createdAt"));
        String periodRaw = resolvePeriod(doc).orElse("");
        Optional<String> expiresAtRaw = inputValueById(doc, "expired");
        String username = inputValueById(doc, "user_name").orElse("");
        String email = inputValueById(doc, "user_mail").orElse("");
        String reserved = resolveReserved(doc)
                .or(() -> resolveReservedFromExistingLicense(externalId))
                .orElse("1");
        Optional<String> statusRaw = resolveStatus(doc);
        Optional<String> commentsOpt = resolveComment(doc);
        if (commentsOpt.isEmpty()) {
            int textareaCount = doc.select("textarea").size();
            log.warn("Missing required comments for ZIS2 externalId={} hasIdCmt={} hasNameCmt={} hasXpathIdCmt={} firstFieldsetCommentRow={} textareaCount={} title='{}'",
                    externalId,
                    doc.getElementById("cmt") != null,
                    doc.selectFirst("textarea[name=cmt]") != null,
                    !doc.selectXpath("//*[@id='cmt']").isEmpty(),
                    doc.selectFirst("fieldset:nth-of-type(1) table tr:has(td b:matchesOwn((?i)комментар))") != null,
                    textareaCount,
                    doc.title());
        }
        String comments = commentsOpt
                .orElseThrow(() -> new IllegalStateException("Required field not found: comments"));
        String offKey = resolveOfflineKey(doc)
                .orElseThrow(() -> new IllegalStateException("Required field not found: offline key"));

        LicenseEntity license = new LicenseEntity();
        license.setExternalId(externalId);
        license.setBrandId(brandId());
        license.setProductId(productId());
        license.setDevices(Integer.valueOf(reserved));
        license.setDescription(comments);

        statusRaw.ifPresentOrElse(raw -> {
            switch (raw) {
                case "0" -> license.setStatus(LicenseStatus.ALLOWED);
                case "1" -> license.setStatus(LicenseStatus.BLOCKED);
                case "2" -> license.setStatus(LicenseStatus.BLOCKED_NEW);
                case "3" -> license.setStatus(LicenseStatus.BLOCKED_OVER);
                default -> log.warn("Unrecognized ZIS2 statusRaw='{}' for externalId={}", raw, externalId);
            }
        }, () -> log.warn("Missing ZIS2 status for externalId={}", externalId));

        WhiteAdminKeyEntity baseKeyEntity = new WhiteAdminKeyEntity();
        baseKeyEntity.setOnlineKey(onKey);
        baseKeyEntity.setOfflineKey(KeyMarkersUtils.removeMarkers(offKey));
        license.setKey(baseKeyEntity);

        NaturalDurationMatcher naturalDurationMatcher = new NaturalDurationMatcher();
        try {
            Optional<BusinessPeriod> match = naturalDurationMatcher.match(periodRaw);
            if (match.isPresent()) {
                license.setBusinessPeriod(match.get());
            } else {
                log.warn("Failed to parse ZIS2 periodRaw='{}' for externalId={}", periodRaw, externalId);
            }
        } catch (MatchingException e) {
            log.warn("Failed to parse ZIS2 periodRaw='{}' for externalId={}: {}", periodRaw, externalId, e.getMessage());
        }

        // "expiresAt" can be legitimately absent for non-activated licenses.
        expiresAtRaw.ifPresent(raw -> parseToInstant(raw).ifPresent(license::setExpiresAt));
        parseToInstant(createdAtRaw).ifPresent(license::setCreatedAtOrigin);

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
        boolean hasOrderId = comments.chars().allMatch(Character::isDigit);
        if (hasOrderId) {
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
                log.debug("Failed to classify WA order reference from ZIS2 comment='{}': {}", comments, ex.getMessage());
            }
        }

        return new LicenseAggregate(license, client, order);
    }

    private Optional<String> resolveActivationCode(Document doc) {
        Optional<String> strict = extractLegendValue(doc, "Активационный код: ");
        if (strict.isPresent()) {
            return strict;
        }
        // Fallback for pages where the first fieldset/legend structure is shifted.
        Element legend = doc.selectFirst("fieldset:nth-of-type(1) legend");
        if (legend != null) {
            Optional<String> parsed = parseActivationFromLegend(legend.text());
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        for (Element anyLegend : doc.select("legend")) {
            Optional<String> parsed = parseActivationFromLegend(anyLegend.text());
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveCreatedAt(Document doc) {
        Optional<String> strict = extractByCss(doc, "fieldset:nth-of-type(1) table tr:nth-of-type(1) td:nth-of-type(2)");
        if (strict.isPresent()) {
            return strict;
        }
        return extractRowValueContaining(doc, "создан");
    }

    private Optional<String> resolvePeriod(Document doc) {
        // Strict source #1: select#term selected option text (not value attr).
        Element termSelect = doc == null ? null : doc.getElementById("term");
        if (termSelect != null) {
            Element selected = termSelect.selectFirst("option[selected]");
            if (selected == null) {
                selected = termSelect.selectFirst("option");
            }
            if (selected != null) {
                Optional<String> text = clean(selected.text());
                if (text.isPresent()) {
                    return text;
                }
            }
        }
        // Strict source #2: first fieldset / second row / second column.
        return extractByCss(doc, "fieldset:nth-of-type(1) table tr:nth-of-type(2) td:nth-of-type(2)");
    }

    private Optional<String> resolveOfflineKey(Document doc) {
        Optional<String> strict = extractByCss(doc, "fieldset:nth-of-type(2) table tr td textarea");
        if (strict.isPresent()) {
            return strict;
        }
        // Fallback: pick textarea from a fieldset that is not the main profile fieldset
        // and does not look like the "comments" textarea.
        for (Element fs : doc.select("fieldset")) {
            Element ta = fs.selectFirst("textarea");
            if (ta == null) {
                continue;
            }
            String name = ta.attr("name");
            String id = ta.id();
            if ("cmt".equalsIgnoreCase(name) || "cmt".equalsIgnoreCase(id)) {
                continue;
            }
            Optional<String> value = clean(ta.val().isBlank() ? ta.text() : ta.val());
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> parseActivationFromLegend(String legendText) {
        if (legendText == null || legendText.isBlank()) {
            return Optional.empty();
        }
        String lower = legendText.toLowerCase();
        if (!lower.contains("активацион") && !lower.contains("activation")) {
            return Optional.empty();
        }
        int colon = legendText.indexOf(':');
        if (colon >= 0 && colon + 1 < legendText.length()) {
            String candidate = legendText.substring(colon + 1).trim();
            return clean(candidate);
        }
        int hash = legendText.indexOf('#');
        if (hash >= 0 && hash + 1 < legendText.length()) {
            String candidate = legendText.substring(hash + 1).trim();
            return clean(candidate);
        }
        // If no delimiter found, do not treat the whole legend label as a key.
        return Optional.empty();
    }

    @Override
    public String name() {
        return "ZIS_2.0_ENRICHER";
    }

    private Optional<String> extractByCss(Document doc, String css) {
        if (doc == null || css == null || css.isBlank()) {
            return Optional.empty();
        }
        Element element = doc.selectFirst(css);
        if (element == null) {
            return Optional.empty();
        }
        String value = element.hasAttr("value") ? element.attr("value") : element.text();
        if ((value == null || value.isBlank()) && "textarea".equalsIgnoreCase(element.tagName())) {
            value = element.val();
        }
        return clean(value);
    }

    private Optional<String> resolveReserved(Document doc) {
        Optional<String> strict = inputValueById(doc, "reserved");
        if (strict.isPresent()) {
            return strict;
        }
        // Legacy fallback when input id is missing or moved.
        Optional<String> byRow = extractRowValueContaining(doc, "резерв");
        if (byRow.isPresent()) {
            return byRow;
        }
        Optional<String> byName = extractByCss(doc, "input[name=reserved]");
        if (byName.isPresent()) {
            return byName;
        }
        Optional<String> fromSelect = selectedOptionValueById(doc, "reserved");
        if (fromSelect.isPresent()) {
            return fromSelect;
        }
        return resolveReservedFromAnyRow(doc);
    }

    private Optional<String> resolveComment(Document doc) {
        Element byId = doc == null ? null : doc.getElementById("cmt");
        if (byId != null) {
            return Optional.of(extractTextareaValueAllowEmpty(byId));
        }
        Element byXpathId = (doc == null || doc.selectXpath("//*[@id='cmt']").isEmpty())
                ? null
                : doc.selectXpath("//*[@id='cmt']").first();
        if (byXpathId != null) {
            return Optional.of(extractTextareaValueAllowEmpty(byXpathId));
        }
        Element byName = doc == null ? null : doc.selectFirst("textarea[name=cmt]");
        if (byName != null) {
            return Optional.of(extractTextareaValueAllowEmpty(byName));
        }
        // Extra fallback for minor DOM shifts around the first fieldset.
        Element firstFieldsetTextarea = doc == null ? null : doc.selectFirst("fieldset:nth-of-type(1) textarea#cmt, fieldset:nth-of-type(1) textarea[name=cmt]");
        if (firstFieldsetTextarea != null) {
            return Optional.of(extractTextareaValueAllowEmpty(firstFieldsetTextarea));
        }
        // Fallback by row label "Комментарии:" in first fieldset table.
        Element row = doc == null ? null : doc.selectFirst("fieldset:nth-of-type(1) table tr:has(td b:matchesOwn((?i)комментар))");
        if (row != null) {
            Element ta = row.selectFirst("td:nth-of-type(2) textarea");
            if (ta != null) {
                return Optional.of(extractTextareaValueAllowEmpty(ta));
            }
        }
        return Optional.empty();
    }

    private String extractTextareaValueAllowEmpty(Element textarea) {
        if (textarea == null) {
            return "";
        }
        String value = textarea.val();
        if (value == null || value.isBlank()) {
            value = textarea.text();
        }
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private Optional<String> resolveStatus(Document doc) {
        Optional<String> strict = selectedOptionValueById(doc, "status");
        if (strict.isPresent()) {
            return strict;
        }
        // Legacy fallback for moved select.
        Element select = doc.selectFirst("select[name=status]");
        if (select == null) {
            return Optional.empty();
        }
        Element selected = select.selectFirst("option[selected]");
        if (selected == null) {
            selected = select.selectFirst("option");
        }
        return selected == null ? Optional.empty() : clean(selected.attr("value"));
    }

    private Optional<String> resolveReservedFromAnyRow(Document doc) {
        if (doc == null) {
            return Optional.empty();
        }
        Pattern digits = Pattern.compile("(\\d+)");
        for (Element row : doc.select("tr")) {
            String rowText = row.text();
            if (rowText == null || rowText.isBlank()) {
                continue;
            }
            String lower = rowText.toLowerCase();
            if (!lower.contains("зарезерв") && !lower.contains("резерв")) {
                continue;
            }

            Element selectedOption = row.selectFirst("option[selected]");
            if (selectedOption != null) {
                Optional<String> val = clean(selectedOption.attr("value"));
                if (val.isPresent()) {
                    return val;
                }
                Optional<String> txt = clean(selectedOption.text());
                if (txt.isPresent()) {
                    return txt;
                }
            }

            Element input = row.selectFirst("input[value]");
            if (input != null) {
                Optional<String> val = clean(input.attr("value"));
                if (val.isPresent()) {
                    return val;
                }
            }

            Matcher m = digits.matcher(rowText);
            if (m.find()) {
                return Optional.of(m.group(1));
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveReservedFromExistingLicense(Long externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        return licenseRepository.findByExternalId(externalId)
                .map(LicenseEntity::getDevices)
                .filter(v -> v != null && v > 0)
                .map(String::valueOf);
    }

}
