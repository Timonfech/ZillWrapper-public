package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.LicenseManagementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ZisTwoLicenseGenerator extends AbstractWhiteAdminPackLicenseGenerator {

    private final String endpoint;
    private final String latestPage;

    public ZisTwoLicenseGenerator(AbstractWhiteAdminClient client,
                                  OrderItemRepository orderItemRepository,
                                  OrderRepository orderRepository,
                                  LicenseManagementService licenseManagementService,
                                  SourceManagementService sourceManagementService,
                                  LicenseRepository licenseRepository,
                                  EnrichmentTaskManager enrichmentTaskManager,
                                  @Value("${whiteAdminPanel.catchupBootstrapWindow:200}") int catchupBootstrapWindow,
                                  @Value("${whiteAdminPanel.catchupEnabled:true}") boolean catchupEnabled,
                                  @Value("${whiteAdminPanel.zis2.latestPage}") String latestPage,
                                  @Value("${whiteAdminPanel.zis2.generatePackPage}") String endpoint) {
        super(client, orderItemRepository, orderRepository, licenseManagementService, sourceManagementService, licenseRepository, enrichmentTaskManager, catchupBootstrapWindow, catchupEnabled);
        this.endpoint = endpoint;
        this.latestPage = latestPage;
    }

    @Override
    public boolean supports(ProductInfo product) {
        return product.brandId() == 2 && product.productId() == 3;
    }

    @Override
    protected String getEndpoint(ProductInfo product) {
        return endpoint;
    }

    @Override
    protected long fetchLatestExternalId(ProductInfo product) {
        try {
            org.jsoup.nodes.Document doc = client().loadDocument(latestPage, null, false);
            org.jsoup.select.Elements tables = doc.select("body > table");
            if (tables.size() < 2) {
                throw new IllegalStateException("ZIS2 latest page structure is invalid");
            }
            org.jsoup.nodes.Element table2 = tables.get(1);
            org.jsoup.select.Elements paymentRows = table2.select("tr.payment_row");
            if (paymentRows.isEmpty()) {
                throw new IllegalStateException("ZIS2 latest page contains no payment rows");
            }
            org.jsoup.nodes.Element latestRow = paymentRows.get(0);
            org.jsoup.nodes.Element firstTd = latestRow.selectFirst("td");
            if (firstTd == null) {
                throw new IllegalStateException("ZIS2 latest page missing id cell");
            }
            return Long.parseLong(firstTd.text().trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch latest ZIS2 external id", e);
        }
    }
}
