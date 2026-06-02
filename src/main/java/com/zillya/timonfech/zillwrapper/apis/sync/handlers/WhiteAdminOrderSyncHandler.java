package com.zillya.timonfech.zillwrapper.apis.sync.handlers;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class WhiteAdminOrderSyncHandler extends AbstractOrderSyncHandler {

    private final String targetUrl;

    public WhiteAdminOrderSyncHandler(
            OrderRepository orderRepository,
            AbstractWhiteAdminClient client,
            @Value("${whiteAdminPanel.orders.listPage:admin_page.php}") String targetUrl) {
        super(orderRepository, client);
        this.targetUrl = targetUrl;
    }

    @Override
    protected void doSync(OrderEntity order) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(order.getWhiteAdminId())));
        params.add(new BasicNameValuePair("do", "save_comment"));
        
        String comment = order.getUserComment() != null ? order.getUserComment() : "";
        params.add(new BasicNameValuePair("comment", comment));

        try {
            client.loadDocument(targetUrl, params, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to sync order comments to WhiteAdmin", e);
        }
    }
}
