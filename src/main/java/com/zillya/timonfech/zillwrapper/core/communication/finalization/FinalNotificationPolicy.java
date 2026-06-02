package com.zillya.timonfech.zillwrapper.core.communication.finalization;

public interface FinalNotificationPolicy {

    String kind();

    boolean supports(FinalNotificationContext context);

    void notify(FinalNotificationContext context);
}
