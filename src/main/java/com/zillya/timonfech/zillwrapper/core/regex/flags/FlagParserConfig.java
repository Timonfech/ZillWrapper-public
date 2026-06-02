package com.zillya.timonfech.zillwrapper.core.regex.flags;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FlagParserConfig {

    @Bean
    @Qualifier("orderFlagParser")
    public FlagParser orderFlagParser() {
        return new FlagParser(List.of(
                new ExcelFlagDefinition(),
                new SubscribeFlagDefinition(),
                new PartnerFlagDefinition(),
                new SubscribeDetailedFlagDefinition(),
                new NotifyClientFlagDefinition(),
                new TextFlagDefinition()
        ));
    }

    @Bean
    @Qualifier("orderParameterFlagParser")
    public ParameterFlagParser orderParameterFlagParser() {
        return new ParameterFlagParser(List.of(
                new LocaleFlagDefinition(),
                new WarningLeadFlagDefinition(),
                new SubscriptionIntervalFlagDefinition()
        ));
    }

    @Bean
    @Qualifier("orderItemParameterFlagParser")
    public ParameterFlagParser orderItemParameterFlagParser() {
        return new ParameterFlagParser(List.of(
                new WarningLeadFlagDefinition(),
                new SubscriptionIntervalFlagDefinition()
        ));
    }
}
