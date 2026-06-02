package com.zillya.timonfech.zillwrapper.core.events;

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
public class OrderPreviewPayload {
    private Long portalId;
    private Long whiteAdminId;
    private String userComment;
    private String email;
    private List<String> emails;
    private String localeTag;
    private boolean localeExplicit;
    private boolean waCreateDecisionRequired;
    private String waDocAddress;
    private String waComment;
    private List<PreviewItem> items;
    private List<DeliveryTargetSpec> targets;
    private String rawTextHash;
}
