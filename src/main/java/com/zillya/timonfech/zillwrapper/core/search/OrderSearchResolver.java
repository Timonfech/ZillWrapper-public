package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderSearchResolver implements SearchResolver<OrderEntity> {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRegistry productRegistry;

    @Override
    public SearchEntityType entityType() {
        return SearchEntityType.ORDER;
    }

    @Override
    public Class<OrderEntity> modelType() {
        return OrderEntity.class;
    }

    @Override
    public List<OrderEntity> resolve(SearchQuery q) {
        Map<Long, OrderEntity> out = new LinkedHashMap<>();
        ProductInfo productFilter = resolveProductFilter(q.productName());
        if (q.productName() != null && !q.productName().isBlank() && productFilter == null) {
            return List.of();
        }
        if (q.orderId() != null) {
            orderRepository.findById(q.orderId()).ifPresent(o -> out.put(o.getId(), o));
        }
        if (q.woid() != null) {
            orderRepository.findAllByWhiteAdminId(q.woid()).forEach(o -> out.put(o.getId(), o));
        }
        if (q.wzid() != null) {
            orderRepository.findAllByWhiteAdminId(q.wzid()).stream()
                    .filter(o -> hasProduct(o, 2, 4))
                    .forEach(o -> out.put(o.getId(), o));
        }
        if (q.wid2() != null) {
            orderRepository.findAllByWhiteAdminId(q.wid2()).stream()
                    .filter(o -> hasProduct(o, 2, 3))
                    .forEach(o -> out.put(o.getId(), o));
        }
        if (q.pid() != null) {
            orderRepository.findAllByPortalId(q.pid()).forEach(o -> out.put(o.getId(), o));
        }
        if (q.comment() != null && !q.comment().isBlank()) {
            orderRepository.findAllByUserCommentNormalized(q.comment()).forEach(o -> out.put(o.getId(), o));
        }
        if (productFilter != null && out.isEmpty()) {
            orderRepository.findAll().stream()
                    .filter(o -> hasProduct(o, productFilter.brandId(), productFilter.productId()))
                    .forEach(o -> out.put(o.getId(), o));
        }
        if (productFilter == null) {
            return new ArrayList<>(out.values());
        }
        return out.values().stream()
                .filter(o -> hasProduct(o, productFilter.brandId(), productFilter.productId()))
                .toList();
    }

    private boolean hasProduct(OrderEntity order, int brandId, int productId) {
        if (order == null || order.getId() == null) {
            return false;
        }
        return orderItemRepository.findByOrderId(order.getId()).stream().anyMatch(i ->
                i != null
                        && i.getProductBrandId() != null
                        && i.getProductId() != null
                        && i.getProductBrandId() == brandId
                        && i.getProductId() == productId
        );
    }

    private ProductInfo resolveProductFilter(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        return productRegistry.findProductByText(productName.trim()).orElse(null);
    }
}
