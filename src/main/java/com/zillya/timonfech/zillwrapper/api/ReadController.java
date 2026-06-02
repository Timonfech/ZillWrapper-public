package com.zillya.timonfech.zillwrapper.api;

import com.zillya.timonfech.zillwrapper.api.auth.ApiAccessPolicyService;
import com.zillya.timonfech.zillwrapper.api.auth.ApiAuthenticationService;
import com.zillya.timonfech.zillwrapper.api.auth.ApiPrincipal;
import com.zillya.timonfech.zillwrapper.api.masking.MaskingFieldType;
import com.zillya.timonfech.zillwrapper.api.masking.MaskingVaultService;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.PhoneContact;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.search.LicenseSearchResolver;
import com.zillya.timonfech.zillwrapper.core.search.OrderSearchResolver;
import com.zillya.timonfech.zillwrapper.core.search.SearchEntityType;
import com.zillya.timonfech.zillwrapper.core.search.SearchQuery;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReadController {

    private final ApiAuthenticationService apiAuthenticationService;
    private final ApiAccessPolicyService accessPolicyService;
    private final MaskingVaultService maskingVaultService;
    private final OrderSearchResolver orderSearchResolver;
    private final LicenseSearchResolver licenseSearchResolver;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final LicenseRepository licenseRepository;

    @GetMapping("/orders")
    public ApiListResponse<OrderDto> orders(@RequestParam(required = false) Long orderId,
                                            @RequestParam(required = false) Long woid,
                                            @RequestParam(required = false) Long wzid,
                                            @RequestParam(required = false) Long wid2,
                                            @RequestParam(required = false) Long pid,
                                            @RequestParam(required = false) Long lex,
                                            @RequestParam(required = false) Long kid,
                                            @RequestParam(required = false) String kon,
                                            @RequestParam(required = false) String kof,
                                            @RequestParam(required = false) String comment,
                                            @RequestParam(required = false, name = "product") String productName,
                                            @RequestParam(required = false) String masking,
                                            @RequestParam(required = false) String maskingSessionId,
                                            @RequestParam(required = false, defaultValue = "100") Integer limit,
                                            HttpServletRequest httpRequest) {
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireReadAccess(principal);

        MaskingResolved maskingResolved = resolveMasking(principal, masking, maskingSessionId);
        SearchQuery query = SearchQuery.builder()
                .entityType(SearchEntityType.ORDER)
                .orderId(orderId)
                .woid(woid)
                .wzid(wzid)
                .wid2(wid2)
                .pid(pid)
                .lex(lex)
                .kid(kid)
                .kon(kon)
                .kof(kof)
                .comment(comment)
                .productName(productName)
                .build();

        List<OrderEntity> found = orderSearchResolver.resolve(query);
        int bounded = Math.min(Math.max(limit == null ? 100 : limit, 1), 500);
        if (found.size() > bounded) {
            found = found.subList(0, bounded);
        }

        List<OrderDto> out = new ArrayList<>();
        for (OrderEntity raw : found) {
            OrderEntity order = orderRepository.findByIdWithDeliveryTargets(raw.getId()).orElse(raw);
            out.add(mapOrder(order, maskingResolved.context()));
        }
        return new ApiListResponse<>(out, materializeMaskingMeta(maskingResolved));
    }

    @GetMapping("/orders/{id}")
    public ApiItemResponse<OrderDto> orderById(@PathVariable Long id,
                                               @RequestParam(required = false) String masking,
                                               @RequestParam(required = false) String maskingSessionId,
                                               HttpServletRequest httpRequest) {
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireReadAccess(principal);
        MaskingResolved maskingResolved = resolveMasking(principal, masking, maskingSessionId);
        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        return new ApiItemResponse<>(mapOrder(order, maskingResolved.context()), materializeMaskingMeta(maskingResolved));
    }

    @GetMapping("/licenses")
    public ApiListResponse<LicenseDto> licenses(@RequestParam(required = false) Long orderId,
                                                @RequestParam(required = false) Long woid,
                                                @RequestParam(required = false) Long wzid,
                                                @RequestParam(required = false) Long wid2,
                                                @RequestParam(required = false) Long pid,
                                                @RequestParam(required = false) Long lex,
                                                @RequestParam(required = false) Long kid,
                                                @RequestParam(required = false) String kon,
                                                @RequestParam(required = false) String kof,
                                                @RequestParam(required = false) String comment,
                                                @RequestParam(required = false, name = "product") String productName,
                                                @RequestParam(required = false) String masking,
                                                @RequestParam(required = false) String maskingSessionId,
                                                @RequestParam(required = false, defaultValue = "100") Integer limit,
                                                HttpServletRequest httpRequest) {
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireReadAccess(principal);
        MaskingResolved maskingResolved = resolveMasking(principal, masking, maskingSessionId);

        SearchQuery query = SearchQuery.builder()
                .entityType(SearchEntityType.LICENSE)
                .orderId(orderId)
                .woid(woid)
                .wzid(wzid)
                .wid2(wid2)
                .pid(pid)
                .lex(lex)
                .kid(kid)
                .kon(kon)
                .kof(kof)
                .comment(comment)
                .productName(productName)
                .build();

        List<LicenseEntity> found = licenseSearchResolver.resolve(query);
        int bounded = Math.min(Math.max(limit == null ? 100 : limit, 1), 500);
        if (found.size() > bounded) {
            found = found.subList(0, bounded);
        }
        List<LicenseDto> out = found.stream().map(l -> mapLicense(l, maskingResolved.context())).toList();
        return new ApiListResponse<>(out, materializeMaskingMeta(maskingResolved));
    }

    @GetMapping("/licenses/{id}")
    public ApiItemResponse<LicenseDto> licenseById(@PathVariable Long id,
                                                   @RequestParam(required = false) String masking,
                                                   @RequestParam(required = false) String maskingSessionId,
                                                   HttpServletRequest httpRequest) {
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireReadAccess(principal);
        MaskingResolved maskingResolved = resolveMasking(principal, masking, maskingSessionId);
        LicenseEntity license = licenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("License not found: " + id));
        return new ApiItemResponse<>(mapLicense(license, maskingResolved.context()), materializeMaskingMeta(maskingResolved));
    }

    private MaskingResolved resolveMasking(ApiPrincipal principal, String maskingParam, String sessionIdParam) {
        boolean requestedMasking = "on".equalsIgnoreCase(maskingParam) || "true".equalsIgnoreCase(maskingParam);
        boolean maskingEnabled = principal.isLlmReadonly() || requestedMasking;

        accessPolicyService.requireMaskingEnabledForRequest(principal, maskingEnabled);
        if (!maskingEnabled) {
            return new MaskingResolved(null, null);
        }

        String sessionId = sessionIdParam;
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = maskingVaultService.startSession(
                    principal.userId(),
                    principal.isLlmReadonly() ? "llm_readonly" : "api_read"
            ).maskingSessionId();
        } else {
            maskingVaultService.touchOwnedSession(sessionId, principal.userId());
        }
        MaskingVaultService.MaskingRenderContext context = maskingVaultService.newRenderContext(sessionId, principal.userId());
        return new MaskingResolved(context, new MaskingMeta(true, sessionId, 0, Map.of()));
    }

    private OrderDto mapOrder(OrderEntity order, MaskingVaultService.MaskingRenderContext maskCtx) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        List<OrderContactDto> contacts = new ArrayList<>();
        if (order.getDeliveryTargets() != null) {
            for (OrderDeliveryTargetEntity target : order.getDeliveryTargets()) {
                if (target == null || target.getContactMethod() == null) {
                    continue;
                }
                ContactMethod method = target.getContactMethod();
                String value = contactValue(method);
                if (maskCtx != null) {
                    var type = method.getType();
                    if (type != null) {
                        value = switch (type) {
                            case EMAIL -> maskCtx.mask(MaskingFieldType.EMAIL, value);
                            case PHONE_NUMBER -> maskCtx.mask(MaskingFieldType.PHONE, value);
                            default -> value;
                        };
                    }
                }
                contacts.add(new OrderContactDto(
                        method.getType() == null ? null : method.getType().name(),
                        value,
                        target.getOutputFormat() == null ? null : target.getOutputFormat().name(),
                        target.isEnabled()
                ));
            }
        }

        String userComment = order.getUserComment();
        String clientComment = order.getClientComment();
        if (maskCtx != null) {
            userComment = maskCtx.mask(MaskingFieldType.NAME, userComment);
            clientComment = maskCtx.mask(MaskingFieldType.NAME, clientComment);
        }
        List<OrderItemDto> itemDtos = items.stream().map(this::mapOrderItem).toList();
        return new OrderDto(
                order.getId(),
                order.getPortalId(),
                order.getWhiteAdminId(),
                order.getOrderStatus() == null ? null : order.getOrderStatus().name(),
                userComment,
                clientComment,
                itemDtos,
                contacts
        );
    }

    private OrderItemDto mapOrderItem(OrderItemEntity item) {
        return new OrderItemDto(
                item.getId(),
                item.getProductBrandId(),
                item.getProductId(),
                item.getCount(),
                item.getPcPerLicense(),
                item.getPeriodAmount(),
                item.getPeriodUnit() == null ? null : item.getPeriodUnit().name(),
                item.getProcessingStatus() == null ? null : item.getProcessingStatus().name(),
                item.getKeyTypes() == null ? List.of() : item.getKeyTypes().stream().map(KeyType::name).toList(),
                item.getOutputTypes() == null ? List.of() : item.getOutputTypes().stream().map(Enum::name).toList()
        );
    }

    private LicenseDto mapLicense(LicenseEntity l, MaskingVaultService.MaskingRenderContext maskCtx) {
        String online = l.getKey() != null ? l.getKey().getOnlineKey() : null;
        String offline = l.getKey() != null ? l.getKey().getOfflineKey() : null;
        String description = l.getDescription();
        if (maskCtx != null) {
            online = maskCtx.mask(MaskingFieldType.ONLINE_KEY, online);
            offline = maskCtx.mask(MaskingFieldType.OFFLINE_KEY, offline);
            description = maskCtx.mask(MaskingFieldType.NAME, description);
        }
        return new LicenseDto(
                l.getId(),
                l.getExternalId(),
                l.getOrderId(),
                l.getOrderItemId(),
                l.getBrandId(),
                l.getProductId(),
                l.getStatus() == null ? null : l.getStatus().name(),
                description,
                l.getExpiresAt() == null ? null : l.getExpiresAt().toString(),
                online,
                offline
        );
    }

    private String contactValue(ContactMethod method) {
        if (method == null) {
            return null;
        }
        if (method instanceof EmailContact emailContact) {
            if (emailContact.getPlainValue() != null && !emailContact.getPlainValue().isBlank()) {
                return emailContact.getPlainValue();
            }
            return emailContact.getEncryptedValue();
        }
        if (method instanceof PhoneContact phoneContact) {
            if (phoneContact.plainValue != null && !phoneContact.plainValue.isBlank()) {
                return phoneContact.plainValue;
            }
            return phoneContact.encryptedValue;
        }
        return null;
    }

    private MaskingMeta materializeMaskingMeta(MaskingResolved resolved) {
        if (resolved == null || resolved.context() == null || resolved.meta() == null) {
            return null;
        }
        return new MaskingMeta(
                true,
                resolved.meta().maskingSessionId(),
                resolved.context().tokenCount(),
                resolved.context().tokenMapMeta()
        );
    }

    public record ApiListResponse<T>(List<T> data, MaskingMeta masking) {
    }

    public record ApiItemResponse<T>(T data, MaskingMeta masking) {
    }

    public record MaskingMeta(boolean enabled, String maskingSessionId, int tokenCount, Map<String, String> tokenMapMeta) {
    }

    private record MaskingResolved(MaskingVaultService.MaskingRenderContext context, MaskingMeta meta) {
    }

    public record OrderDto(
            Long id,
            Long portalId,
            Long whiteAdminId,
            String orderStatus,
            String userComment,
            String clientComment,
            List<OrderItemDto> items,
            List<OrderContactDto> contacts
    ) {
    }

    public record OrderItemDto(
            Long id,
            Integer productBrandId,
            Integer productId,
            Integer count,
            Integer pcPerLicense,
            Integer periodAmount,
            String periodUnit,
            String processingStatus,
            List<String> keyTypes,
            List<String> outputTypes
    ) {
    }

    public record OrderContactDto(
            String type,
            String value,
            String outputFormat,
            boolean enabled
    ) {
    }

    public record LicenseDto(
            Long id,
            Long externalId,
            Long orderId,
            Long orderItemId,
            Integer brandId,
            Integer productId,
            String status,
            String description,
            String expiresAt,
            String onlineKey,
            String offlineKey
    ) {
    }
}
