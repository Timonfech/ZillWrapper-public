package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "email.routing")
public class EmailRoutingProperties {

    @Valid
    private List<Rule> rules = new ArrayList<>();

    @Getter
    @Setter
    public static class Rule {
        @NotBlank
        private String id;
        private OperationType operationType;
        private ContactMethodType contactType;
        private boolean enabled = true;
        @NotBlank
        private String template;
        @Valid
        private SubjectKeys subjectKeys = new SubjectKeys();
    }

    @Getter
    @Setter
    public static class SubjectKeys {
        @NotBlank
        private String single = "email.license.subject.single";
        @NotBlank
        private String plural = "email.license.subject.plural";
        @NotBlank
        private String offlineSuffix = "email.license.subject.suffix.offline";
    }
}
