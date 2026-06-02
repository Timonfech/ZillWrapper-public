package com.zillya.timonfech.zillwrapper.core.pending;

import com.zillya.timonfech.zillwrapper.core.routing.DeliveryTargetSpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewPendingPayload implements PendingTaskPayload {
    private Long sourceId;
    private Long portalId;
    private Long whiteAdminId;
    private String userComment;
    private String email;
    private List<String> emails;
    private String localeTag;
    private boolean payedReady;
    private boolean waCreateDecisionRequired;
    private String waDocAddress;
    private String waComment;
    private List<OrderPreviewPendingItem> items;
    private List<DeliveryTargetSpec> deliveryTargets;
    private String rawTextHash;
}
