package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminActivationEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.repos.WhiteAdminActivationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhiteAdminActivationProvider implements ActivationProvider {

    private final AbstractWhiteAdminClient client;
    private final WhiteAdminActivationRepository whiteAdminActivationRepository;
    @Value("${whiteAdminPanel.zab.activationsPage}")
    private String zabActivationsUrl;
    @Value("${whiteAdminPanel.zis2.activationsPage}")
    private String zis2ActivationsUrl;

    @Override
    public boolean supports(ActivationProviderType providerType) {
        return providerType == ActivationProviderType.WHITE_ADMIN;
    }

    @Override
    public void enrich(Long keyId, Long externalId, Integer productId) throws Exception {
        if (keyId == null || productId == null) {
            return;
        }
        List<WhiteAdminActivationEntity> activations = switch (productId) {
            case 4 -> loadZabActivations(externalId);
            case 3 -> loadZis2Activations(externalId);
            default -> List.of();
        };
        WhiteAdminKeyEntity keyRef = new WhiteAdminKeyEntity();
        keyRef.setId(keyId);
        activations.forEach(a -> a.setWhiteAdminKey(keyRef));
        whiteAdminActivationRepository.deleteByWhiteAdminKey_Id(keyId);
        whiteAdminActivationRepository.saveAll(activations);
    }

    private List<WhiteAdminActivationEntity> loadZabActivations(Long externalId) throws Exception {
        List<WhiteAdminActivationEntity> result = new ArrayList<>();
        List<NameValuePair> params = List.of(new BasicNameValuePair("id", String.valueOf(externalId)));
        Document doc = client.loadDocument(zabActivationsUrl, params, false);
        for (Element row : doc.select("tr")) {
            Elements tds = row.select("td");
            if (tds.size() < 5) {
                continue;
            }
            String pcid = tds.get(1).text().trim();
            if (pcid.isBlank() || "PCID".equalsIgnoreCase(pcid)) {
                continue;
            }
            WhiteAdminActivationEntity activation = new WhiteAdminActivationEntity();
            activation.setPcid(pcid);
            parseToInstant(tds.get(2).text().trim()).ifPresent(activation::setFirstActivation);
            parseToInstant(tds.get(3).text().trim()).ifPresent(activation::setLastActivation);
            try {
                activation.setComputersActivated(Integer.parseInt(tds.get(4).text().trim()));
            } catch (Exception ignored) {
            }
            result.add(activation);
        }
        return result;
    }

    private List<WhiteAdminActivationEntity> loadZis2Activations(Long externalId) throws Exception {
        List<WhiteAdminActivationEntity> result = new ArrayList<>();
        List<NameValuePair> params = List.of(new BasicNameValuePair("id", String.valueOf(externalId)));
        Document doc = client.loadDocument(zis2ActivationsUrl, params, false);
        for (Element row : doc.select("tr")) {
            Elements tds = row.select("td");
            if (tds.size() < 4) {
                continue;
            }
            String pcid = tds.get(0).text().trim();
            if (pcid.isBlank() || "PCID".equalsIgnoreCase(pcid)) {
                continue;
            }
            WhiteAdminActivationEntity activation = new WhiteAdminActivationEntity();
            activation.setPcid(pcid);
            parseToInstant(tds.get(1).text().trim()).ifPresent(activation::setFirstActivation);
            parseToInstant(tds.get(2).text().trim()).ifPresent(activation::setLastActivation);
            result.add(activation);
        }
        return result;
    }

    private java.util.Optional<Instant> parseToInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || "-".equals(dateStr)) {
            return java.util.Optional.empty();
        }
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return java.util.Optional.of(LocalDateTime.parse(dateStr, dtf)
                    .atZone(ZoneOffset.UTC)
                    .toInstant());
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse activation date: {}", dateStr);
            return java.util.Optional.empty();
        }
    }
}
