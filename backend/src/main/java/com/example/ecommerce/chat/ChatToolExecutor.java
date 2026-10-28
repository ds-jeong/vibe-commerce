package com.example.ecommerce.chat;

import java.util.List;
import java.util.Map;

public interface ChatToolExecutor {

    List<Map<String, Object>> definitions();

    String execute(String name, String argumentsJson, String userMessage);
}
