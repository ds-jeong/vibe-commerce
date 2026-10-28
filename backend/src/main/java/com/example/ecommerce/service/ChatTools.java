package com.example.ecommerce.service;

import com.example.ecommerce.chat.ChatToolExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ChatTools implements ChatToolExecutor {

    private final OrderLookupTools orderLookupTools;
    private final ReturnRequestTools returnRequestTools;

    public ChatTools(OrderLookupTools orderLookupTools, ReturnRequestTools returnRequestTools) {
        this.orderLookupTools = orderLookupTools;
        this.returnRequestTools = returnRequestTools;
    }

    @Override
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        definitions.addAll(orderLookupTools.definitions());
        definitions.addAll(returnRequestTools.definitions());
        return List.copyOf(definitions);
    }

    @Override
    public String execute(String name, String argumentsJson, String userMessage) {
        if (returnRequestTools.handles(name)) {
            return returnRequestTools.execute(name, argumentsJson, userMessage);
        }
        return orderLookupTools.execute(name, argumentsJson);
    }
}
