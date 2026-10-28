package com.example.ecommerce.controller;

import com.example.ecommerce.chat.ChatRateLimitedException;
import com.example.ecommerce.chat.ChatRateLimiter;
import com.example.ecommerce.chat.ChatRequest;
import com.example.ecommerce.chat.ChatResponse;
import com.example.ecommerce.chat.LlmException;
import com.example.ecommerce.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatRateLimiter chatRateLimiter = mock(ChatRateLimiter.class);
    private final ChatController controller = new ChatController(chatService, chatRateLimiter);

    @Test
    void successReturnsPlainReply() {
        when(chatService.reply(org.mockito.ArgumentMatchers.any())).thenReturn("안녕하세요.");

        ResponseEntity<ChatResponse> response = controller.chat(new ChatRequest("안녕", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("SUCCESS", response.getBody().status());
        assertEquals("안녕하세요.", response.getBody().reply());
        assertEquals(null, response.getBody().message());
    }

    @Test
    void validationAndProviderErrorsUseFailBody() {
        when(chatService.reply(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("메시지를 입력해 주세요."))
                .thenThrow(new LlmException("챗봇 응답을 받지 못했습니다."));

        ResponseEntity<ChatResponse> invalid = controller.chat(new ChatRequest(" ", null));
        assertEquals(400, invalid.getStatusCode().value());
        assertEquals("FAIL", invalid.getBody().status());
        assertEquals("메시지를 입력해 주세요.", invalid.getBody().message());

        ResponseEntity<ChatResponse> unavailable = controller.chat(new ChatRequest("안녕", null));
        assertEquals(503, unavailable.getStatusCode().value());
        assertEquals("FAIL", unavailable.getBody().status());
        assertEquals("챗봇 응답을 받지 못했습니다.", unavailable.getBody().message());
        assertEquals(null, unavailable.getBody().reply());
    }

    @Test
    void rateLimitDoesNotCallTheChatService() {
        doThrow(new ChatRateLimitedException("요청이 많습니다. 잠시 후 다시 시도해 주세요."))
                .when(chatRateLimiter).acquireCurrentUser();

        ResponseEntity<ChatResponse> response = controller.chat(new ChatRequest("안녕", null));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("FAIL", response.getBody().status());
        assertEquals("요청이 많습니다. 잠시 후 다시 시도해 주세요.", response.getBody().message());
        assertEquals(null, response.getBody().reply());
        verify(chatService, never()).reply(org.mockito.ArgumentMatchers.any());
    }
}
