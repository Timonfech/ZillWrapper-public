package com.zillya.timonfech.zillwrapper.core.communication.sections;

public interface ControlMessageSectionRenderer {

    boolean supports(ControlMessageContext context);

    int order();

    String render(ControlMessageContext context);
}

