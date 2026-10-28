package com.example.ecommerce.chat;

import java.util.List;
import java.util.Map;

public record LlmCompletion(String content, List<LlmToolCall> toolCalls, List<Map<String, Object>> providerSteps) {

    public LlmCompletion(String content, List<LlmToolCall> toolCalls) {
        this(content, toolCalls, List.of());
    }

    public LlmCompletion {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        providerSteps = providerSteps == null ? List.of() : List.copyOf(providerSteps);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
