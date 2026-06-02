package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhiteAdminPayedOrderIdsProvider {
    @Value("${whiteAdminPanel.orders.listPage}")
    private String targetUrl;

    @Value("${whiteAdminPanel.orders.payedSuffix}")
    private String payedSuffix;

    private final AbstractWhiteAdminClient client;

    public List<Long> getPayedIds() {
        List<NameValuePair> params = new ArrayList<>();
        for (String pair : this.payedSuffix.split("&")) {
            String[] keyValue = pair.split("=", 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            params.add(new BasicNameValuePair(key, value));
        }
        try {
            Document doc = this.client.loadDocument(this.targetUrl, params, false);
            Elements tr = doc.select("tr.payment_row");
            return tr.stream()
                    .map(Element::firstElementChild)
                    .filter(Objects::nonNull)
                    .map(el -> el.text().trim())
                    .flatMap(text -> {
                        try {
                            return Stream.of(Long.parseLong(text));
                        } catch (NumberFormatException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch PAYED order ids", e);
            return List.of(-1L);
        }
    }
}

