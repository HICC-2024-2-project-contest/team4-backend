package com.team4.giftidea.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI API 연동을 위한 설정 클래스
 */
@Configuration
@Slf4j
public class GptConfig {

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.api.url}")
    private String apiUrl;

    /**
     * OpenAI API 요청을 위한 RestTemplate 빈 생성
     *
     * @return OpenAI API 호출을 위한 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String authHeader = "Bearer " + openAiKey;
            request.getHeaders().add("Authorization", authHeader);
            request.getHeaders().add("Content-Type", "application/json");
            return execution.execute(request, body);
        });
        return restTemplate;
    }

    public String getModel() {
        return model;
    }

    public String getApiUrl() {
        return apiUrl;
    }
}