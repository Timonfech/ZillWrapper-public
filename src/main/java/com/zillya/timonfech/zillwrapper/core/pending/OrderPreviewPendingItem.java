package com.zillya.timonfech.zillwrapper.core.pending;

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
public class OrderPreviewPendingItem {
    private int brandId;
    private int productId;
    private int count;
    private BusinessPeriod period;
    private int computers;
    private List<OutputType> outputTypes;
    private List<KeyType> keyTypes;
    private boolean subscribed;
    private OrderItemOptions options;
}
