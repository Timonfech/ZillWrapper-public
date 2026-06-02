package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Email implementation of {@link CommunicationService}.
 * Renders a Thymeleaf HTML-mode template and sends it as a MIME email.
 *
 * Template must be located in: classpath:/templates/{template}.html
 * The "from" address is resolved from application.properties (spring.mail.username).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCommunicationService implements CommunicationService {

    private final TemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    @Value("${spring.mail.username:}")
    private String configuredFrom;

    @Override
    public void send(String toAddress, String template, Map<String, Object> variables, Locale locale) {
        send(toAddress, template, variables, locale, List.of());
    }

    public void send(String toAddress,
                     String template,
                     Map<String, Object> variables,
                     Locale locale,
                     List<IArtifact> attachments) {
        send(toAddress,
                new ResolvedEmailTemplate(
                        "legacy-default",
                        template,
                        "email.license.subject.single",
                        "email.license.subject.plural",
                        "email.license.subject.suffix.offline"
                ),
                variables,
                locale,
                attachments);
    }

    public void send(String toAddress,
                     ResolvedEmailTemplate resolvedTemplate,
                     Map<String, Object> variables,
                     Locale locale,
                     List<IArtifact> attachments) {
        Context ctx = new Context(locale);
        ctx.setVariables(variables);

        String html = templateEngine.process(resolvedTemplate.template(), ctx);
        String subject = resolveSubject(variables, locale, resolvedTemplate);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toAddress);
            if (configuredFrom != null && !configuredFrom.isBlank()) {
                helper.setFrom(configuredFrom.trim());
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("Email subject resolved empty for template=" + resolvedTemplate.template());
            }
            helper.setSubject(subject);
            helper.setText(html, true); // true = isHtml

            if (attachments != null) {
                for (IArtifact artifact : attachments) {
                    if (artifact == null || artifact.getContent() == null || artifact.getFilename() == null) {
                        continue;
                    }
                    helper.addAttachment(artifact.getFilename(), new ByteArrayResource(artifact.getContent()));
                }
            }

            mailSender.send(message);
            log.info("Email sent via template {} (rule={})",
                    resolvedTemplate.template(),
                    resolvedTemplate.ruleId());
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage(), e);
            throw new RuntimeException("Email send failed", e);
        }
    }

    private String resolveSubject(Map<String, Object> variables, Locale locale, ResolvedEmailTemplate resolvedTemplate) {
        String productName = Objects.toString(variables.get("productName"), "").trim();
        Object countRaw = variables.get("subjectCount");
        int count = countRaw instanceof Number n ? n.intValue() : 1;
        boolean plural = count > 1;
        boolean offlineRequested = Boolean.TRUE.equals(variables.get("subjectOfflineRequested"));

        Locale effective = normalizeSubjectLocale(locale);
        MessageSource routedMessageSource = buildRoutedMessageSource(resolvedTemplate.template());
        String baseKey = plural ? resolvedTemplate.subjectPluralKey() : resolvedTemplate.subjectSingleKey();
        String base = routedMessageSource.getMessage(baseKey, new Object[]{productName}, effective);
        if (!offlineRequested) {
            return base;
        }
        String suffix = routedMessageSource.getMessage(resolvedTemplate.subjectOfflineSuffixKey(), null, effective);
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        String normalizedSuffix = suffix.startsWith(" ") ? suffix : " " + suffix;
        return base + normalizedSuffix;
    }

    private Locale normalizeSubjectLocale(Locale locale) {
        if (locale == null) {
            return Locale.forLanguageTag("uk");
        }
        String lang = locale.getLanguage();
        if ("ru".equalsIgnoreCase(lang)) {
            return Locale.forLanguageTag("uk");
        }
        return locale;
    }

    private MessageSource buildRoutedMessageSource(String templateName) {
        List<String> bundles = new ArrayList<>();
        if (templateName != null && !templateName.isBlank()) {
            addBundleIfExists(bundles, "messages_email_" + templateName);
            if (templateName.endsWith("_email")) {
                addBundleIfExists(bundles, "messages_email_" + templateName.substring(0, templateName.length() - "_email".length()));
            }
        }
        addBundleIfExists(bundles, "messages_email_common");
        addBundleIfExists(bundles, "messages_email");
        if (bundles.isEmpty()) {
            bundles.add("messages_email");
        }
        ResourceBundleMessageSource src = new ResourceBundleMessageSource();
        src.setBasenames(bundles.toArray(String[]::new));
        src.setDefaultEncoding("UTF-8");
        src.setUseCodeAsDefaultMessage(true);
        src.setParentMessageSource(messageSource);
        return src;
    }

    private void addBundleIfExists(List<String> bundles, String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return;
        }
        String path = baseName.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = EmailCommunicationService.class.getClassLoader();
        }
        boolean exists = cl.getResource(path + ".properties") != null
                || cl.getResource(path + "_en.properties") != null
                || cl.getResource(path + "_uk.properties") != null;
        if (exists) {
            bundles.add(baseName);
        }
    }
}
