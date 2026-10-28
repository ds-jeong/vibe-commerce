package com.example.ecommerce.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatRateLimiterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsEachAuthenticatedUserAndResetsAfterOneMinute() {
        AtomicLong now = new AtomicLong(Instant.parse("2026-10-28T00:00:00Z").toEpochMilli());
        ChatRateLimiter limiter = new ChatRateLimiter(properties(2), clock(now));
        login("alice");

        assertDoesNotThrow(limiter::acquireCurrentUser);
        assertDoesNotThrow(limiter::acquireCurrentUser);
        ChatRateLimitedException limited = assertThrows(ChatRateLimitedException.class, limiter::acquireCurrentUser);
        assertEquals("요청이 많습니다. 잠시 후 다시 시도해 주세요.", limited.getMessage());

        login("bob");
        assertDoesNotThrow(limiter::acquireCurrentUser);

        login("alice");
        now.addAndGet(60_000L);
        assertDoesNotThrow(limiter::acquireCurrentUser);
    }

    @Test
    void anonymousCallsAreNotCounted() {
        ChatRateLimiter limiter = new ChatRateLimiter(properties(1), Clock.systemUTC());

        assertDoesNotThrow(limiter::acquireCurrentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        assertDoesNotThrow(limiter::acquireCurrentUser);
    }

    @Test
    void invalidLimitFallsBackToTen() {
        LlmProperties properties = properties(0);
        assertEquals(10, properties.boundedMaxRequestsPerMinute());
        properties.setMaxRequestsPerMinute(100);
        assertEquals(30, properties.boundedMaxRequestsPerMinute());
    }

    private static void login(String userKey) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userKey, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static LlmProperties properties(int limit) {
        LlmProperties properties = new LlmProperties();
        properties.setMaxRequestsPerMinute(limit);
        return properties;
    }

    private static Clock clock(AtomicLong millis) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }
        };
    }
}
