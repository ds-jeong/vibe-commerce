package com.example.ecommerce.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {

    static final String MESSAGE = "요청이 많습니다. 잠시 후 다시 시도해 주세요.";
    private static final long WINDOW_MILLIS = 60_000L;

    private final LlmProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ChatRateLimiter(LlmProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ChatRateLimiter(LlmProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void acquireCurrentUser() {
        String userKey = currentUserKey();
        if (userKey == null) {
            return;
        }
        long now = clock.millis();
        int limit = properties.boundedMaxRequestsPerMinute();
        if (windows.size() > 1000) {
            long cutoff = now - WINDOW_MILLIS;
            windows.entrySet().removeIf(entry -> entry.getValue().startedAtMillis() < cutoff);
        }
        Window window = windows.compute(userKey, (key, current) -> {
            if (current == null || now - current.startedAtMillis() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            return new Window(current.startedAtMillis(), current.count() + 1);
        });
        if (window.count() > limit) {
            throw new ChatRateLimitedException(MESSAGE);
        }
    }

    private static String currentUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof String userKey)) {
            return null;
        }
        if (userKey.isBlank() || "anonymousUser".equals(userKey)) {
            return null;
        }
        return userKey;
    }

    private record Window(long startedAtMillis, int count) {
    }
}
