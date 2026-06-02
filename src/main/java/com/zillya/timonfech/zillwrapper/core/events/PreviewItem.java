package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreviewItem {
    private Integer brandId;
    private Integer productId;
    private String productName;
    private int count;
    private int pcPerLicense;
    private BusinessPeriod period;
    private List<KeyType> keyTypes;
    private List<OutputType> outputTypes;
    private boolean subscribed;
    private OrderItemOptions options;
}
