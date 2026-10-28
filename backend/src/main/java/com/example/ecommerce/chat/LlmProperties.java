package com.example.ecommerce.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.8-flash";
    private int timeoutSeconds = 15;
    private int maxRequestsPerMinute = 10;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public int boundedTimeoutSeconds() {
        if (timeoutSeconds < 1) {
            return 15;
        }
        return Math.min(timeoutSeconds, 60);
    }

    public int boundedMaxRequestsPerMinute() {
        if (maxRequestsPerMinute < 1) {
            return 10;
        }
        return Math.min(maxRequestsPerMinute, 30);
    }
}
