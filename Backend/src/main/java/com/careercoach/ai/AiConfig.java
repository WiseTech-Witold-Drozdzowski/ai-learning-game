package com.careercoach.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Beans for the {@code ai} module — the OpenRouter HTTP client and its settings. */
@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class AiConfig {

    @Bean
    RestClient openRouterRestClient(OpenRouterProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }
}
