package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class ExcelLicenseReportGenerator {

    public byte[] generate(List<LicenseEntity> licenses) {
        return generate(licenses, List.of());
    }

    public byte[] generate(List<LicenseEntity> licenses, List<OrderItemEntity> items) {
        return generateInternal(licenses, items, false, false, l -> "");
    }

    public byte[] generateArtifactReport(List<LicenseEntity> licenses,
                                         List<OrderItemEntity> items,
                                         Function<LicenseEntity, String> productNameResolver) {
        return generateInternal(licenses, items, true, true, productNameResolver);
    }

    private byte[] generateInternal(List<LicenseEntity> licenses,
                                    List<OrderItemEntity> items,
                                    boolean includeProductColumn,
                                    boolean normalizeOfflineKeys,
                                    Function<LicenseEntity, String> productNameResolver) {
        log.debug("Generating Excel for {} licenses", licenses.size());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Licenses");

            Map<Long, OrderItemEntity> itemById = new HashMap<>();
            for (OrderItemEntity item : items) {
                if (item.getId() != null) {
                    itemById.put(item.getId(), item);
                }
            }

            boolean includeOnline = false;
            boolean includeOffline = false;
            for (LicenseEntity license : licenses) {
                OrderItemEntity item = license.getOrderItemId() == null ? null : itemById.get(license.getOrderItemId());
                List<KeyType> requested = item == null || item.getKeyTypes() == null || item.getKeyTypes().isEmpty()
                        ? List.of(KeyType.ONLINE)
                        : item.getKeyTypes();
                if (requested.contains(KeyType.ONLINE)) {
                    includeOnline = true;
                }
                if (requested.contains(KeyType.OFFLINE)) {
                    includeOffline = true;
                }
            }

            boolean includeExpiresAt = licenses.stream().anyMatch(l -> l.getExpiresAt() != null);

            List<String> headers = new java.util.ArrayList<>();
            if (includeOnline) {
                headers.add("Key (Online)");
            }
            if (includeOffline) {
                headers.add("Key (Offline)");
            }
            headers.add("Created At");
            if (includeExpiresAt) {
                headers.add("Expires At");
            }
            if (includeProductColumn) {
                headers.add(0, "Product");
            }
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            int rowIdx = 1;
            for (LicenseEntity license : licenses) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                if (includeProductColumn) {
                    String productName = productNameResolver.apply(license);
                    row.createCell(col++).setCellValue(productName == null ? "" : productName);
                }
                if (includeOnline) {
                    String online = (license.getKey() != null && license.getKey().getOnlineKey() != null)
                            ? license.getKey().getOnlineKey()
                            : "";
                    row.createCell(col++).setCellValue(online);
                }
                if (includeOffline) {
                    String offline = (license.getKey() != null && license.getKey().getOfflineKey() != null)
                            ? license.getKey().getOfflineKey()
                            : "";
                    if (normalizeOfflineKeys
                            && offline != null
                            && !offline.isBlank()
                            && license.getKey() instanceof WhiteAdminKeyEntity) {
                        offline = KeyMarkersUtils.addMarkers(KeyMarkersUtils.removeMarkers(offline));
                    }
                    row.createCell(col++).setCellValue(offline);
                }
                row.createCell(col++).setCellValue(license.getCreatedAt() != null ? license.getCreatedAt().toString() : "");
                if (includeExpiresAt) {
                    row.createCell(col).setCellValue(license.getExpiresAt() != null ? license.getExpiresAt().toString() : "");
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate Excel report: {}", e.getMessage());
            throw new RuntimeException("Excel generation failed", e);
        }
    }
}
