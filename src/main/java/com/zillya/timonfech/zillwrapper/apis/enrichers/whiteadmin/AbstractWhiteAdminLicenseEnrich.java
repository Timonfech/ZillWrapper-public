package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.AbstractEntityEnrich;
import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.*;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LicenseAggregate;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LicenseUpsertResult;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@Slf4j
public abstract class AbstractWhiteAdminLicenseEnrich extends AbstractEntityEnrich<LicenseEntity> {

    protected final AbstractWhiteAdminClient client;
    protected final LicenseExternalIdResolver externalIdResolver;
    protected final LicenseDedupService dedupService;
    protected final LicenseRepository licenseRepository;
    protected final EnrichmentActivationRuntimeService activationRuntimeService;

    protected AbstractWhiteAdminLicenseEnrich(
            ApplicationEventPublisher publisher,
            EnrichmentProgressRegistry progressRegistry,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
            AbstractWhiteAdminClient client,
            LicenseExternalIdResolver externalIdResolver,
            LicenseDedupService dedupService,
            LicenseRepository licenseRepository,
            EnrichmentActivationRuntimeService activationRuntimeService
    ) {
        super(publisher, progressRegistry, enrichmentTaskExecutor);
        this.client = client;
        this.externalIdResolver = externalIdResolver;
        this.dedupService = dedupService;
        this.licenseRepository = licenseRepository;
        this.activationRuntimeService = activationRuntimeService;
    }

    @Override
    protected ExternalIdResolver resolver() {
        return externalIdResolver;
    }

    @Override
    public LicenseEntity targetType() {
        return new LicenseEntity();
    }


    protected abstract Integer brandId();

    protected abstract Integer productId();


    protected abstract String targetUrl();

    protected abstract LicenseAggregate parse(Document doc, Long externalId);

    @Override
    @Transactional
    protected boolean processSingle(EnrichmentRequest ctx, Long externalId) {
        try {
            Document doc = loadDocument(externalId);
            LicenseAggregate aggregate = parse(doc, externalId);

            LicenseUpsertResult result = dedupService.upsert(ctx, aggregate);
            progressRegistry.markLicenseProcessed(ctx.taskId());
            activationRuntimeService.enqueueIfRequired(
                    ctx.taskId(),
                    result.licenseId(),
                    externalId,
                    ctx.sourceId(),
                    aggregate.license() == null ? null : aggregate.license().getProductId(),
                    ActivationProviderType.WHITE_ADMIN,
                    aggregate.license() != null && aggregate.license().getExpiresAt() != null
            );

            if (result.licenseChanged()) {
                publishEntityUpdated(
                        ctx.sourceId(),
                        EntityTypeEnum.LICENSE,
                        result.licenseId()
                );
            }

            if (result.orderChanged() && result.orderId() != null) {
                publishEntityUpdated(
                        ctx.sourceId(),
                        EntityTypeEnum.ORDER,
                        result.orderId()
                );
            }

            if (result.clientChanged() && result.clientId() != null) {
                publishEntityUpdated(
                        ctx.sourceId(),
                        EntityTypeEnum.CLIENT,
                        result.clientId()
                );
            }

            return result.stopScanning();
        } catch (IllegalStateException e) {
            if ("SKIP_DELETED_LICENSE".equals(e.getMessage())) {
                log.info("Skip deleted/empty WhiteAdmin license card externalId={}", externalId);
                return false;
            }
            throw e;
        } catch (IOException e) {
            log.warn("Failed to load WhiteAdmin document for id={}", externalId, e);
            return false;
        }
    }

    protected Document loadDocument(Long externalId) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(externalId)));
        return client.loadDocument(targetUrl(), params, false);
    }

    protected Optional<String> inputValueById(Document doc, String id) {
        Element el = doc.getElementById(id);
        if (el == null) return Optional.empty();
        String v = el.hasAttr("value") ? el.attr("value") : el.text();
        return clean(v);
    }

    protected Optional<String> textareaValueById(Document doc, String id) {
        Element el = doc.getElementById(id);
        if (el == null) return Optional.empty();
        return clean(el.text().isEmpty() ? el.val() : el.text());
    }

    protected Optional<String> selectedOptionValueById(Document doc, String id) {
        Element select = doc.getElementById(id);
        if (select == null) return Optional.empty();
        Element selected = select.selectFirst("option[selected]");
        if (selected == null) selected = select.selectFirst("option");
        return selected == null ? Optional.empty() : clean(selected.attr("value"));
    }

    protected Optional<String> extractRowValue(Document doc, String label) {
        for (Element tr : doc.select("tr")) {
            Element first = tr.selectFirst("td b");
            if (first != null && label.equals(first.text())) {
                List<Element> tds = tr.select("td");
                if (tds.size() >= 2) {
                    Element secondTd = tds.get(1);
                    Element select = secondTd.selectFirst("select");
                    if (select != null) {
                        Element selected = select.selectFirst("option[selected]");
                        if (selected == null) selected = select.selectFirst("option");
                        if (selected != null) {
                            return clean(selected.text());
                        }
                    }
                    return clean(secondTd.text());
                }
            }
        }
        return Optional.empty();
    }

    protected Optional<String> extractRowValueContaining(Document doc, String labelPart) {
        for (Element tr : doc.select("tr")) {
            Element first = tr.selectFirst("td b");
            if (first == null) {
                continue;
            }
            String firstText = first.text();
            if (firstText == null || !firstText.toLowerCase().contains(labelPart.toLowerCase())) {
                continue;
            }
            List<Element> tds = tr.select("td");
            if (tds.size() < 2) {
                continue;
            }
            Element secondTd = tds.get(1);
            Element select = secondTd.selectFirst("select");
            if (select != null) {
                Element selected = select.selectFirst("option[selected]");
                if (selected != null) {
                    return clean(selected.text());
                }
            }
            return clean(secondTd.text());
        }
        return Optional.empty();
    }

    protected Optional<String> extractLegendValue(Document doc, String prefix) {
        for (Element legend : doc.select("legend")) {
            String text = legend.text();
            if (text != null && text.startsWith(prefix)) {
                return clean(text.substring(prefix.length()).trim());
            }
        }
        return Optional.empty();
    }

    protected Optional<String> extractTextareaByLegend(Document doc, String legendPrefix) {
        for (Element fieldset : doc.select("fieldset")) {
            Element legend = fieldset.selectFirst("legend");
            if (legend != null && legend.text() != null && legend.text().startsWith(legendPrefix)) {
                Element ta = fieldset.selectFirst("textarea");
                if (ta != null) {
                    String v = ta.val();
                    if (v == null || v.isBlank()) v = ta.text();
                    return clean(v);
                }
            }
        }
        return Optional.empty();
    }

    protected Optional<String> clean(String s) {
        if (s == null) return Optional.empty();
        String v = s.replace("\u00a0", " ").trim();
        return v.isEmpty() || v.equals("-") ? Optional.empty() : Optional.of(v);
    }

    protected boolean isDeletedByCreatedAtMarker(Optional<String> createdAtRaw) {
        return createdAtRaw.map(String::trim)
                .filter(v -> !v.isBlank())
                .map(v -> "1970-01-01 00:00:00".equals(v))
                .orElse(false);
    }

    @Override
    public boolean supports(EnrichmentRequest request) {
        return request.entityType() == EntityTypeEnum.LICENSE
                && Objects.equals(request.brandId(), this.brandId())
                && Objects.equals(request.productId(), this.productId());
    }
}
