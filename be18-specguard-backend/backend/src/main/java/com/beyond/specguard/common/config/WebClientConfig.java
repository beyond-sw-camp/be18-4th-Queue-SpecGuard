package com.beyond.specguard.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class WebClientConfig {

    @Value("${python.api.base-url}")
    private String pythonApiBaseUrl;

    @Bean
    public WebClient pythonWebClient(WebClient.Builder builder){
        return builder
                .baseUrl(pythonApiBaseUrl)
                .build();
    }
}
