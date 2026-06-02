package com.zillya.timonfech.zillwrapper.core.pipeline.actions;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhiteAdminStatusUpdateService {
    private final AbstractWhiteAdminClient client;

    @Value("${whiteAdminPanel.zab.detailsPage}")
    private String zabDetailsPage;
    @Value("${whiteAdminPanel.zab.editPage:admin_corp.php}")
    private String zabEditPage;

    @Value("${whiteAdminPanel.zis2.detailsPage}")
    private String zis2DetailsPage;
    @Value("${whiteAdminPanel.zis2.editPage:admin_lic.php}")
    private String zis2EditPage;

    public boolean updateStatus(LicenseEntity license, LicenseStatus targetStatus) {
        if (license == null || license.getExternalId() == null) {
            return false;
        }
        if (!isSupportedWhiteAdminProduct(license)) {
            return false;
        }
        if (!(license.getKey() instanceof WhiteAdminKeyEntity key) || key.getOnlineKey() == null || key.getOnlineKey().isBlank()) {
            return false;
        }
        String statusRaw = mapWhiteAdminStatus(targetStatus);
        if (statusRaw == null) {
            return false;
        }
        try {
            boolean zab = isZab(license);
            String detailsPage = zab ? zabDetailsPage : zis2DetailsPage;
            String editPage = zab ? zabEditPage : zis2EditPage;
            Document details = client.loadDocument(
                    detailsPage,
                    List.of(new BasicNameValuePair("id", String.valueOf(license.getExternalId()))),
                    false
            );
            List<NameValuePair> params = buildChangeParams(details, license.getExternalId(), key.getOnlineKey(), statusRaw, zab);
            client.executeGetWithRetry(editPage, params, true);

            Document after = client.loadDocument(
                    detailsPage,
                    List.of(new BasicNameValuePair("id", String.valueOf(license.getExternalId()))),
                    false
            );
            String actualStatus = resolveCurrentSelectValueById(after, "status").orElse(null);
            boolean applied = statusRaw.equals(actualStatus);
            if (!applied) {
                log.warn("WhiteAdmin status post-check mismatch for licenseId={} externalId={}: expectedStatus={} actualStatus={}",
                        license.getId(), license.getExternalId(), statusRaw, actualStatus);
            }
            return applied;
        } catch (Exception ex) {
            log.warn("WhiteAdmin status update failed for licenseId={} externalId={}: {}",
                    license.getId(), license.getExternalId(), ex.getMessage());
            return false;
        }
    }

    private List<NameValuePair> buildChangeParams(Document details, Long externalId, String onlineKey, String statusRaw, boolean zab) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("cmd", "change"));
        if (externalId != null) {
            params.add(new BasicNameValuePair("id", String.valueOf(externalId)));
        }
        params.add(new BasicNameValuePair("lic", onlineKey));
        put(params, "created", inputValue(details, "created"));
        put(params, "term", inputValue(details, "term"));
        put(params, "expired", inputValue(details, "expired"));
        put(params, "user_name", inputValue(details, "user_name"));
        put(params, "user_mail", inputValue(details, "user_mail"));
        put(params, "reserved", inputValue(details, "reserved"));
        put(params, "status", statusRaw);
        put(params, "cmt", textAreaValue(details, "cmt"));
        if (zab) {
            put(params, "server_number", inputValue(details, "server_number"));
            put(params, "company", inputValue(details, "company"));
        }
        return params;
    }

    private void put(List<NameValuePair> params, String key, String value) {
        params.add(new BasicNameValuePair(key, value == null ? "" : value));
    }

    private String inputValue(Document doc, String id) {
        Element el = doc.getElementById(id);
        if (el == null) {
            return "";
        }
        if (el.hasAttr("value")) {
            return el.attr("value").trim();
        }
        return el.text() == null ? "" : el.text().trim();
    }

    private String textAreaValue(Document doc, String id) {
        Element el = doc.getElementById(id);
        if (el == null) {
            return "";
        }
        String val = el.val();
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        return el.text() == null ? "" : el.text().trim();
    }

    private boolean isZab(LicenseEntity license) {
        return Integer.valueOf(2).equals(license.getBrandId()) && Integer.valueOf(4).equals(license.getProductId());
    }

    private boolean isSupportedWhiteAdminProduct(LicenseEntity license) {
        return (Integer.valueOf(2).equals(license.getBrandId()) && Integer.valueOf(4).equals(license.getProductId()))
                || (Integer.valueOf(2).equals(license.getBrandId()) && Integer.valueOf(3).equals(license.getProductId()));
    }

    private String mapWhiteAdminStatus(LicenseStatus status) {
        return switch (status) {
            case ALLOWED -> "0";
            case BLOCKED -> "1";
            default -> null;
        };
    }

    private Optional<String> resolveCurrentSelectValueById(Document doc, String id) {
        Element select = doc.getElementById(id);
        if (select == null) {
            return Optional.empty();
        }
        if (select.hasAttr("value")) {
            String v = select.attr("value");
            return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
        }
        Element selected = select.selectFirst("option[selected]");
        if (selected != null) {
            String v = selected.attr("value");
            return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
        }
        return Optional.empty();
    }
}
