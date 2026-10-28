package com.example.ecommerce.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String status, String reply, String message) {

    public static ChatResponse success(String reply) {
        return new ChatResponse("SUCCESS", reply, null);
    }

    public static ChatResponse fail(String message) {
        return new ChatResponse("FAIL", null, message);
    }
}
