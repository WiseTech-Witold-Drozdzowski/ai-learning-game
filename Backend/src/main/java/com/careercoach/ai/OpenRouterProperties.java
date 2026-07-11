package com.careercoach.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** OpenRouter connection settings (base URL, API key, default model). */
@ConfigurationProperties(prefix = "careercoach.ai.openrouter")
@Getter
@Setter
public class OpenRouterProperties {

    private String baseUrl = "https://openrouter.ai/api/v1";
    private String apiKey = "";
    private String model = "openai/gpt-4o-mini";
}
