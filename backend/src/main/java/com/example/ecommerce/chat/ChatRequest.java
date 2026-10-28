package com.example.ecommerce.chat;

import java.util.List;

public record ChatRequest(String message, List<ChatTurn> history) {
}
