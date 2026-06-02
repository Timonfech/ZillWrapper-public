package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTemplateRoutingServiceTest {

    @Test
    void shouldMatchByOperationTypeAndContactType() {
        EmailRoutingProperties properties = new EmailRoutingProperties();
        properties.setRules(List.of(
                rule("notify-email", OperationType.LICENSE_FULFILLMENT),
                rule("resend-email", OperationType.RESEND_NOTIFICATION)
        ));
        EmailTemplateRoutingService service = new EmailTemplateRoutingService(properties);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setContactMethod(new EmailContact("a@b.c"));
        target.setOutputFormat(OutputType.EXCEL); // Should not affect routing.

        ResolvedEmailTemplate resolved = service.resolve(OperationType.LICENSE_FULFILLMENT, target, new OrderItemEntity());
        assertEquals("notify-email", resolved.ruleId());
        assertEquals("license_email", resolved.template());
    }

    @Test
    void shouldIgnoreOutputFormatInTargetForResend() {
        EmailRoutingProperties properties = new EmailRoutingProperties();
        properties.setRules(List.of(rule("resend-email", OperationType.RESEND_NOTIFICATION)));
        EmailTemplateRoutingService service = new EmailTemplateRoutingService(properties);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setContactMethod(new EmailContact("x@y.z"));
        target.setOutputFormat(OutputType.TEXT);

        ResolvedEmailTemplate resolved = service.resolve(OperationType.RESEND_NOTIFICATION, target, new OrderItemEntity());
        assertEquals("resend-email", resolved.ruleId());
    }

    @Test
    void shouldSkipDisabledRule() {
        EmailRoutingProperties properties = new EmailRoutingProperties();

        EmailRoutingProperties.Rule disabled = rule("notify-disabled", OperationType.LICENSE_FULFILLMENT);
        disabled.setEnabled(false);
        EmailRoutingProperties.Rule enabled = rule("notify-enabled", OperationType.LICENSE_FULFILLMENT);

        properties.setRules(List.of(disabled, enabled));
        EmailTemplateRoutingService service = new EmailTemplateRoutingService(properties);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setContactMethod(new EmailContact("a@b.c"));

        ResolvedEmailTemplate resolved = service.resolve(OperationType.LICENSE_FULFILLMENT, target, new OrderItemEntity());
        assertEquals("notify-enabled", resolved.ruleId());
    }

    @Test
    void shouldFailFastWhenNoRuleMatches() {
        EmailRoutingProperties properties = new EmailRoutingProperties();
        properties.setRules(List.of(rule("notify-email", OperationType.LICENSE_FULFILLMENT)));
        EmailTemplateRoutingService service = new EmailTemplateRoutingService(properties);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setContactMethod(new EmailContact("x@y.z"));

        assertThrows(IllegalStateException.class,
                () -> service.resolve(OperationType.RESEND_NOTIFICATION, target, new OrderItemEntity()));
    }

    @Test
    void shouldFailFastWhenMultipleRulesMatchSameSelectors() {
        EmailRoutingProperties properties = new EmailRoutingProperties();
        properties.setRules(List.of(
                rule("notify-email-1", OperationType.LICENSE_FULFILLMENT),
                rule("notify-email-2", OperationType.LICENSE_FULFILLMENT)
        ));
        EmailTemplateRoutingService service = new EmailTemplateRoutingService(properties);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setContactMethod(new EmailContact("x@y.z"));

        assertThrows(IllegalStateException.class,
                () -> service.resolve(OperationType.LICENSE_FULFILLMENT, target, new OrderItemEntity()));
    }

    private EmailRoutingProperties.Rule rule(String id, OperationType operationType) {
        EmailRoutingProperties.Rule rule = new EmailRoutingProperties.Rule();
        rule.setId(id);
        rule.setOperationType(operationType);
        rule.setContactType(ContactMethodType.EMAIL);
        rule.setTemplate("license_email");
        rule.getSubjectKeys().setSingle("email.license.subject.single");
        rule.getSubjectKeys().setPlural("email.license.subject.plural");
        rule.getSubjectKeys().setOfflineSuffix("email.license.subject.suffix.offline");
        return rule;
    }
}
