package com.zillya.timonfech.zillwrapper.core.communication;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Configures Thymeleaf for email templates and shared i18n message bundles.
 */
@Configuration
@EnableConfigurationProperties({
        EmailRoutingProperties.class
})
public class ThymeleafConfig {

    @Bean
    public SpringTemplateEngine templateEngine(MessageSource messageSource) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setMessageSource(messageSource);

        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("templates/");
        htmlResolver.setSuffix(".html");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("UTF-8");
        htmlResolver.setOrder(1);
        htmlResolver.setCheckExistence(true);
        engine.addTemplateResolver(htmlResolver);

        return engine;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource src = new ResourceBundleMessageSource();
        src.setBasenames("messages_telegram", "messages_email_license", "messages_email", "messages");
        src.setDefaultEncoding("UTF-8");
        src.setUseCodeAsDefaultMessage(true);
        return src;
    }
}
