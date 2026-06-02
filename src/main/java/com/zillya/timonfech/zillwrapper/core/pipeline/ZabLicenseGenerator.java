package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.LicenseManagementService;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ZabLicenseGenerator extends AbstractWhiteAdminPackLicenseGenerator {

    private final String zabEndPoint;
    private final String zabLatestPage;

    public ZabLicenseGenerator(AbstractWhiteAdminClient client,
                               OrderItemRepository orderItemRepository,
                               OrderRepository orderRepository,
                               LicenseManagementService licenseManagementService,
                               SourceManagementService sourceManagementService,
                               LicenseRepository licenseRepository,
                               EnrichmentTaskManager enrichmentTaskManager,
                               @Value("${whiteAdminPanel.catchupBootstrapWindow:200}") int catchupBootstrapWindow,
                               @Value("${whiteAdminPanel.catchupEnabled:true}") boolean catchupEnabled,
                               @Value("${whiteAdminPanel.zab.latestPage}") String zabLatestPage,
                                @Value("${whiteAdminPanel.zab.generatePackPage}") String zabEndPoint) {
        super(client, orderItemRepository, orderRepository, licenseManagementService, sourceManagementService, licenseRepository, enrichmentTaskManager, catchupBootstrapWindow, catchupEnabled);
        this.zabEndPoint = zabEndPoint;
        this.zabLatestPage = zabLatestPage;
    }

    @Override
    public boolean supports(ProductInfo product) {
        return product.brandId() == 2 && product.productId() == 4;
    }

    @Override
    protected String getEndpoint(ProductInfo product) {
        return this.zabEndPoint;
    }

    @Override
    protected List<NameValuePair> extendParams(List<NameValuePair> base,
                                               OrderItemEntity item,
                                               ProductInfo product) {
        String serverNumber = item.getServerNumber() == null ? "1" : String.valueOf(item.getServerNumber());
        base.add(new BasicNameValuePair("server_number", serverNumber));
        return base;
    }

    @Override
    protected long fetchLatestExternalId(ProductInfo product) {
        try {
            org.jsoup.nodes.Document doc = client().loadDocument(zabLatestPage, null, false);
            org.jsoup.select.Elements paymentRows = doc.select("tr.payment_row");
            if (paymentRows.isEmpty()) {
                throw new IllegalStateException("ZAB latest page contains no payment rows");
            }
            org.jsoup.nodes.Element latestRow = paymentRows.get(0);
            org.jsoup.nodes.Element idCell = latestRow.selectFirst("td:nth-child(1)");
            if (idCell == null) {
                throw new IllegalStateException("ZAB latest page missing id cell");
            }
            return Long.parseLong(idCell.text().trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch latest ZAB external id", e);
        }
    }
}
