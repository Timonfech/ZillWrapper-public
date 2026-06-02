package com.zillya.timonfech.zillwrapper.core.communication;

import java.util.Locale;
import java.util.Map;

/**
 * Generic abstraction for sending templated messages.
 * In this project it is used for email rendering/sending.
 */
public interface CommunicationService {

    /**
     * Renders a template and dispatches it to the target.
     *
     * @param targetId    Channel-specific recipient identifier (email address, etc.)
     * @param template    Classpath-relative template name without extension
     * @param variables   Model variables exposed to the template
     * @param locale      Locale used for message resolution (#{...} expressions)
     */
    void send(String targetId, String template, Map<String, Object> variables, Locale locale);
}
