package com.example.ecommerce.chat;

public class ChatRateLimitedException extends RuntimeException {

    public ChatRateLimitedException(String message) {
        super(message);
    }
}
