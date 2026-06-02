package com.zillya.timonfech.zillwrapper.core.interfaces;

import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;

public interface IDeliveryTarget {
    ContactMethod getContactMethod();
    OutputType getOutputFormat();
}
