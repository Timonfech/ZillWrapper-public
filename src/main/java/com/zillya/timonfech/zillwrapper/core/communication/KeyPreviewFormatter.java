package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeyPreviewFormatter {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private final ProductRegistry productRegistry;

    public String renderForOrder(List<LicenseEntity> licenses, List<OrderItemEntity> items) {
        List<LicenseEntity> sorted = licenses == null ? List.of() : licenses.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LicenseEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (sorted.isEmpty()) {
            return "Keys: not available.";
        }

        StringBuilder sb = new StringBuilder("Keys:");
        for (LicenseEntity l : sorted) {
            String on = shortPrefix(l.getKey() != null ? l.getKey().getOnlineKey() : null, 8);
            String off = shortPrefix(l.getKey() != null ? l.getKey().getOfflineKey() : null, 8);
            sb.append("\n- #").append(l.getId()).append(": ")
                    .append("ON=").append(on);
            if (!"-".equals(off)) {
                sb.append(", OFF=").append(off);
            }
        }
        return sb.toString();
    }

    public String renderForLicense(LicenseEntity current, List<LicenseEntity> orderLicenses, List<OrderItemEntity> items) {
        if (current == null) {
            return "Keys: not available.";
        }
        String on = shortPrefix(current.getKey() != null ? current.getKey().getOnlineKey() : null, 8);
        String off = shortPrefix(current.getKey() != null ? current.getKey().getOfflineKey() : null, 8);
        if ("-".equals(on) && "-".equals(off)) {
            return "key=-";
        }
        if (!"-".equals(on) && !"-".equals(off)) {
            return "key: ON=" + on + ", OFF=" + off;
        }
        if (!"-".equals(on)) {
            return "key: ON=" + on;
        }
        return "key: OFF=" + off;
    }

    public String renderFinalRequested(List<LicenseEntity> licenses, List<OrderItemEntity> items) {
        if (items == null || items.isEmpty()) {
            return "Keys: not available.";
        }
        Map<Long, List<LicenseEntity>> byItem = (licenses == null ? List.<LicenseEntity>of() : licenses).stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getOrderItemId() != null)
                .collect(Collectors.groupingBy(LicenseEntity::getOrderItemId, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder("Keys (requested):");
        boolean hasAny = false;
        for (OrderItemEntity item : items) {
            if (item == null || item.getId() == null || item.getKeyTypes() == null || item.getKeyTypes().isEmpty()) {
                continue;
            }
            Set<KeyType> requested = Set.copyOf(item.getKeyTypes());
            List<LicenseEntity> itemLicenses = byItem.getOrDefault(item.getId(), List.of());
            if (itemLicenses.isEmpty()) {
                continue;
            }

            List<String> parts = new ArrayList<>(2);
            if (requested.contains(KeyType.ONLINE)) {
                parts.add("ON=" + collectPrefixes(itemLicenses, KeyType.ONLINE));
            }
            if (requested.contains(KeyType.OFFLINE)) {
                parts.add("OFF=" + collectPrefixes(itemLicenses, KeyType.OFFLINE));
            }
            if (parts.isEmpty()) {
                continue;
            }
            hasAny = true;
            sb.append("\n- item ").append(item.getId()).append(": ").append(String.join(", ", parts));
        }
        return hasAny ? sb.toString() : "Keys: not available.";
    }

    public String renderSearchSummary(List<LicenseEntity> licenses, List<OrderItemEntity> items) {
        List<LicenseEntity> safeLicenses = licenses == null ? List.of() : licenses.stream().filter(Objects::nonNull).toList();
        if (safeLicenses.isEmpty()) {
            return "Found licenses: 0";
        }
        Map<Long, OrderItemEntity> itemMap = (items == null ? List.<OrderItemEntity>of() : items).stream()
                .filter(Objects::nonNull)
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(OrderItemEntity::getId, i -> i, (a, b) -> a, LinkedHashMap::new));

        Set<KeyType> requestedTypes = safeLicenses.stream()
                .map(LicenseEntity::getOrderItemId)
                .filter(Objects::nonNull)
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .map(OrderItemEntity::getKeyTypes)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        String requested = requestedTypes.isEmpty()
                ? "-"
                : requestedTypes.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(t -> t == KeyType.ONLINE ? "ON" : "OFF")
                .collect(Collectors.joining("+"));

        List<Long> ids = safeLicenses.stream().map(LicenseEntity::getId).filter(Objects::nonNull).sorted().toList();
        String idRange = compressIds(ids);

        String products = safeLicenses.stream()
                .map(this::resolveProductNameForDisplay)
                .filter(v -> v != null && !v.isBlank() && !"-".equals(v))
                .distinct()
                .limit(4)
                .collect(Collectors.joining(", "));
        if (products.isBlank()) {
            products = "-";
        }

        String termPc = safeLicenses.stream()
                .map(l -> renderTermPc(l, itemMap.get(l.getOrderItemId())))
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .limit(3)
                .collect(Collectors.joining("; "));
        if (termPc.isBlank()) {
            termPc = "-";
        }

        String expiry = safeLicenses.stream()
                .map(LicenseEntity::getExpiresAt)
                .filter(Objects::nonNull)
                .sorted()
                .findFirst()
                .map(DATE_FMT::format)
                .orElse("-");

        return "Found licenses: " + safeLicenses.size()
                + "\nproducts=" + products
                + "\nprofile=" + termPc
                + "\nids=" + idRange
                + "\nrequested=" + requested
                + "\nexp=" + expiry;
    }

    private String collectPrefixes(List<LicenseEntity> itemLicenses, KeyType keyType) {
        List<String> prefixes = itemLicenses.stream()
                .sorted(Comparator.comparing(LicenseEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .map(l -> keyType == KeyType.ONLINE
                        ? shortPrefix(l.getKey() != null ? l.getKey().getOnlineKey() : null, 8)
                        : shortPrefix(l.getKey() != null ? l.getKey().getOfflineKey() : null, 8))
                .filter(v -> v != null && !v.isBlank() && !"-".equals(v))
                .distinct()
                .toList();
        if (prefixes.isEmpty()) {
            return "-";
        }
        return String.join(", ", prefixes);
    }

    private String shortPrefix(String value, int len) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String norm = KeyMarkersUtils.removeMarkers(value).replaceAll("\\s+", "").trim();
        if (norm.isBlank()) {
            return "-";
        }
        return norm.length() <= len ? norm : norm.substring(0, len);
    }

    public String resolveProductNameForDisplay(LicenseEntity l) {
        Integer productId = l.getProductId();
        Integer brandId = l.getBrandId();
        if (productId == null || brandId == null) {
            return "-";
        }
        return productRegistry.getProductById(productId)
                .filter(p -> p.brandId() == brandId)
                .map(p -> p.getName(Locale.ENGLISH, false, true))
                .orElse(brandId + "/" + productId);
    }

    private String renderTermPc(LicenseEntity l, OrderItemEntity item) {
        int pc = 1;
        if (item != null && item.getPcPerLicense() != null && item.getPcPerLicense() > 0) {
            pc = item.getPcPerLicense();
        } else if (l.getDevices() != null && l.getDevices() > 0) {
            pc = l.getDevices();
        }

        Integer amount = item != null && item.getPeriodAmount() != null ? item.getPeriodAmount() : l.getPeriodAmount();
        String unit = item != null && item.getPeriodUnit() != null
                ? item.getPeriodUnit().name()
                : (l.getPeriodUnit() != null ? l.getPeriodUnit().name() : null);
        if (amount == null || unit == null) {
            return pc + "pc, -";
        }
        return pc + "pc, " + amount + shortUnit(unit);
    }

    private String shortUnit(String unit) {
        String lower = unit.toLowerCase(Locale.ROOT);
        if (lower.startsWith("day")) return "d";
        if (lower.startsWith("month")) return "m";
        if (lower.startsWith("year")) return "y";
        if (lower.startsWith("hour")) return "h";
        return lower;
    }

    private String compressIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "-";
        }
        List<Long> sorted = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return "-";
        }
        List<String> out = new ArrayList<>();
        long start = sorted.getFirst();
        long prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            long cur = sorted.get(i);
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            out.add(start == prev ? String.valueOf(start) : start + "-" + prev);
            start = prev = cur;
        }
        out.add(start == prev ? String.valueOf(start) : start + "-" + prev);
        return String.join(",", out);
    }
}
