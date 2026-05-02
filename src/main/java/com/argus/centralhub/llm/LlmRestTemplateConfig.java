package com.argus.centralhub.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LlmRestTemplateConfig {

    @Bean("llmRestTemplate")
    public RestTemplate llmRestTemplate(LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout().getConnectMs());
        factory.setReadTimeout(properties.getTimeout().getReadMs());
        return new RestTemplate(factory);
    }
}
