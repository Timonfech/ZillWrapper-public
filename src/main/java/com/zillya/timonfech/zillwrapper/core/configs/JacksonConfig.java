package com.zillya.timonfech.zillwrapper.core.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public JsonMapper inboundMapper() {
        return JsonMapper.builder()

                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                .addModule(new JavaTimeModule())

                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

                .build();
    }
}