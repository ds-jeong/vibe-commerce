package com.example.ecommerce.controller;

import com.example.ecommerce.chat.ChatRateLimitedException;
import com.example.ecommerce.chat.ChatRateLimiter;
import com.example.ecommerce.chat.ChatRequest;
import com.example.ecommerce.chat.ChatResponse;
import com.example.ecommerce.chat.LlmException;
import com.example.ecommerce.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatRateLimiter chatRateLimiter;

    public ChatController(ChatService chatService, ChatRateLimiter chatRateLimiter) {
        this.chatService = chatService;
        this.chatRateLimiter = chatRateLimiter;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody(required = false) ChatRequest request) {
        try {
            chatRateLimiter.acquireCurrentUser();
            return ResponseEntity.ok(ChatResponse.success(chatService.reply(request)));
        } catch (ChatRateLimitedException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ChatResponse.fail(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ChatResponse.fail(exception.getMessage()));
        } catch (LlmException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ChatResponse.fail(exception.getMessage()));
        }
    }
}
