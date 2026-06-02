package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.interfaces.ExcelArtifact;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseOrderArtifactGenerator implements OrderArtifactGenerator {

    private final OrderItemRepository orderItemRepository;
    private final LicenseRepository licenseRepository;
    private final OrderRepository orderRepository;
    private final ProductRegistry productRegistry;
    private final ExcelLicenseReportGenerator excelGenerator;

    @Override
    public boolean supports(OrderOperationContext context) {
        return context.getOrderId() != null;
    }

    @Override
    public OperationResult<?> generate(OrderOperationContext context) {
        Long orderId = context.getOrderId();
        if (orderId == null) {
            return OperationResult.fail("Cannot generate license artifacts without orderId", false);
        }

        log.info("Generating license artifacts for order {}", orderId);
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        Long scopedOrderItemId = parseLongToken(context.getCommandPayload(), "rid_order_item_id");
        if (scopedOrderItemId != null) {
            items = items.stream().filter(i -> scopedOrderItemId.equals(i.getId())).toList();
        }

        boolean needsExcel = items.stream()
                .flatMap(item -> item.getOutputTypes().stream())
                .anyMatch(type -> type == OutputType.EXCEL);

        if (needsExcel) {
            String ref = resolveArtifactReference(orderId);
            List<OrderItemEntity> orderedItems = items.stream()
                    .sorted((a, b) -> {
                        if (a.getId() == null && b.getId() == null) return 0;
                        if (a.getId() == null) return 1;
                        if (b.getId() == null) return -1;
                        return Long.compare(a.getId(), b.getId());
                    })
                    .toList();
            int itemRef = 0;
            for (OrderItemEntity item : orderedItems) {
                itemRef++;
                if (item.getOutputTypes() == null || !item.getOutputTypes().contains(OutputType.EXCEL)) {
                    continue;
                }
                if (item.getId() == null) {
                    continue;
                }
                List<LicenseEntity> itemLicenses = licenseRepository.findByOrderItemId(item.getId());
                if (itemLicenses.isEmpty()) {
                    continue;
                }
                byte[] excelData = excelGenerator.generateArtifactReport(itemLicenses, List.of(item), this::resolveProductName);
                ExcelArtifact artifact = new ExcelArtifact("licenses_" + ref + "_" + itemRef + ".xlsx", excelData);
                context.addItemArtifact(item.getId(), artifact);
            }
        }

        for (OrderItemEntity item : items) {
            if (item.getProcessingStatus() == ItemProcessingStatus.GENERATED) {
                item.setProcessingStatus(ItemProcessingStatus.ARTIFACTS_READY);
                orderItemRepository.save(item);
            }
        }

        return OperationResult.ok(null);
    }

    private Long parseLongToken(String payload, String key) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*(\\d+)(?:\\s|$)")
                .matcher(payload);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveArtifactReference(Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getWhiteAdminId() != null) {
                        return String.valueOf(order.getWhiteAdminId());
                    }
                    if (order.getPortalId() != null) {
                        return String.valueOf(order.getPortalId());
                    }
                    String comment = order.getUserComment();
                    if (comment != null && !comment.isBlank()) {
                        return sanitize(comment);
                    }
                    return String.valueOf(orderId);
                })
                .orElse(String.valueOf(orderId));
    }

    private String sanitize(String value) {
        String cleaned = value.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (cleaned.isBlank()) {
            return "order";
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private String resolveProductName(LicenseEntity license) {
        Integer brandId = license.getBrandId();
        Integer productId = license.getProductId();
        if (brandId == null || productId == null) {
            return "";
        }
        return productRegistry.getProductById(productId)
                .filter(productInfo -> productInfo.brandId() == brandId)
                .map(productInfo -> productInfo.getName(Locale.ENGLISH, true, true))
                .orElseGet(() -> brandId + "/" + productId);
    }
}
