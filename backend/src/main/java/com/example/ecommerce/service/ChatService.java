package com.example.ecommerce.service;

import com.example.ecommerce.chat.ChatRequest;
import com.example.ecommerce.chat.ChatToolExecutor;
import com.example.ecommerce.chat.ChatTurn;
import com.example.ecommerce.chat.LlmClient;
import com.example.ecommerce.chat.LlmCompletion;
import com.example.ecommerce.chat.LlmException;
import com.example.ecommerce.chat.LlmToolCall;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatService {

    public static final String SYSTEM_PROMPT = """
            당신은 VibeCommerce 상점의 안내 도우미입니다.
            로그인한 회원의 주문과 배송은 list_my_orders, get_my_order 도구 결과에 있는 사실만 안내합니다.
            도구 결과에 없는 주문번호, 상태, 운송장, 배송일, 도착 예정, 배송사 위치는 알지 못한다고 답합니다.
            liveCarrierStatus와 deliveredAt는 항상 비어 있습니다. 운송장 번호가 있어도 배송사 추적 결과는 없습니다.
            반품 정책은 explain_return_policy 결과에만 근거해 설명합니다. 코드에 없는 반품 기한이나 상품 상태 조건은 만들지 않습니다.
            특정 주문의 반품 가능 여부는 check_return_eligibility 결과로만 판단합니다. 정책 설명만으로 가능하다고 단정하지 않습니다.
            반품 접수는 submit_return_request만 사용합니다. 호출 전에 주문번호, 상품, 사유를 요약하고, 사용자가 반품 신청 확인이라고 답한 뒤에만 호출합니다.
            환불, 결제 취소, 재고 변경은 하지 않습니다. 그런 요청은 마이페이지에서 직접 진행하라고 안내합니다.
            도구 인자로 넘어온 사용자 식별자는 무시합니다.
            답변은 일반 텍스트로만 작성합니다.
            """;

    static final int MAX_MESSAGE_LENGTH = 1000;
    static final int MAX_HISTORY = 10;
    static final int MAX_TOOL_ROUNDS = 3;
    static final int MAX_TOOL_CALLS_PER_ROUND = 2;
    static final int MAX_TOOL_RESULT_CHARS = 4000;
    static final int MAX_REPLY_CHARS = 4000;
    static final String TOOL_LIMIT_MESSAGE = "주문 조회를 마무리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final String TOOL_FAILURE = "{\"error\":\"주문 조회 중 문제가 생겼습니다.\"}";
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");

    private final LlmClient llmClient;
    private final ChatToolExecutor toolExecutor;

    public ChatService(LlmClient llmClient, ChatToolExecutor toolExecutor) {
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
    }

    public String reply(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        String message = normalize(request.message(), "메시지를 입력해 주세요.");
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (request.history() != null) {
            if (request.history().size() > MAX_HISTORY) {
                throw new IllegalArgumentException("대화 이력은 10개까지 보낼 수 있습니다.");
            }
            for (ChatTurn turn : request.history()) {
                ChatTurn valid = validateHistory(turn);
                messages.add(Map.of("role", valid.role(), "content", valid.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", message));
        requireUserKey();

        List<Map<String, Object>> tools = toolExecutor.definitions();
        if (tools == null) {
            tools = List.of();
        }
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            boolean allowTools = round < MAX_TOOL_ROUNDS;
            LlmCompletion completion = llmClient.completeWithTools(messages, allowTools ? tools : List.of());
            if (!completion.hasToolCalls()) {
                return limitReply(completion.content());
            }
            if (!allowTools) {
                return TOOL_LIMIT_MESSAGE;
            }
            messages.add(assistantMessage(completion));
            int executed = 0;
            int index = 0;
            for (LlmToolCall call : completion.toolCalls()) {
                String result = executed < MAX_TOOL_CALLS_PER_ROUND
                        ? dispatch(call, message)
                        : "{\"error\":\"한 번에 조회할 수 있는 횟수를 초과했습니다.\"}";
                messages.add(toolMessage(toolCallId(call, index), call.name(), bound(result)));
                executed++;
                index++;
            }
        }
        return TOOL_LIMIT_MESSAGE;
    }

    private String dispatch(LlmToolCall call, String userMessage) {
        if (ReturnRequestTools.SUBMIT_RETURN_REQUEST.equals(call.name())
                && !ReturnRequestTools.userConfirmed(userMessage)) {
            return "{\"accepted\":false,\"message\":\"사용자 확인 전에는 반품을 접수할 수 없습니다.\"}";
        }
        return safeExecute(call, userMessage);
    }

    private String safeExecute(LlmToolCall call, String userMessage) {
        try {
            String result = toolExecutor.execute(call.name(), call.arguments(), userMessage);
            return result == null ? TOOL_FAILURE : result;
        } catch (RuntimeException exception) {
            return TOOL_FAILURE;
        }
    }

    private Map<String, Object> assistantMessage(LlmCompletion completion) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", completion.content());
        List<Map<String, Object>> calls = new ArrayList<>();
        int index = 0;
        for (LlmToolCall call : completion.toolCalls()) {
            String id = toolCallId(call, index);
            calls.add(Map.of(
                    "id", id,
                    "type", "function",
                    "function", Map.of(
                            "name", call.name() == null ? "" : call.name(),
                            "arguments", call.arguments() == null ? "" : call.arguments())));
            index++;
        }
        message.put("tool_calls", calls);
        if (!completion.providerSteps().isEmpty()) {
            message.put("provider_steps", completion.providerSteps());
        }
        return message;
    }

    private static String toolCallId(LlmToolCall call, int index) {
        if (call.id() == null || call.id().isBlank()) {
            return "call-" + index;
        }
        return call.id();
    }

    private Map<String, Object> toolMessage(String toolCallId, String name, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("name", name == null ? "" : name);
        message.put("content", content);
        return message;
    }

    private String bound(String value) {
        if (value.length() <= MAX_TOOL_RESULT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_TOOL_RESULT_CHARS);
    }

    private String limitReply(String content) {
        if (content == null || content.isBlank()) {
            throw new LlmException("챗봇 응답을 받지 못했습니다.");
        }
        String trimmed = content.trim();
        if (trimmed.length() <= MAX_REPLY_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_REPLY_CHARS);
    }

    private ChatTurn validateHistory(ChatTurn turn) {
        if (turn == null || turn.role() == null || !ALLOWED_ROLES.contains(turn.role())) {
            throw new IllegalArgumentException("허용되지 않는 메시지 역할입니다.");
        }
        return new ChatTurn(turn.role(), normalize(turn.content(), "대화 내용이 비어 있습니다."));
    }

    private String normalize(String value, String blankMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("메시지는 1000자까지 입력할 수 있습니다.");
        }
        return trimmed;
    }

    private static void requireUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof String userKey)) {
            throw new IllegalArgumentException("로그인 후 이용해 주세요.");
        }
        if (userKey.isBlank() || "anonymousUser".equals(userKey)) {
            throw new IllegalArgumentException("로그인 후 이용해 주세요.");
        }
    }
}
