package com.example.ecommerce.chat;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    String complete(List<ChatTurn> messages);

    LlmCompletion completeWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools);
}
