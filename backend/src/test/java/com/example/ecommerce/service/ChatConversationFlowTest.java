package com.example.ecommerce.service;

import com.example.ecommerce.chat.ChatRequest;
import com.example.ecommerce.chat.ChatTurn;
import com.example.ecommerce.chat.LlmClient;
import com.example.ecommerce.chat.LlmCompletion;
import com.example.ecommerce.chat.LlmException;
import com.example.ecommerce.chat.LlmToolCall;
import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationFlowTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ReturnRequestService returnRequestService;

    private ScriptedLlmClient llm;
    private ChatService chat;

    @BeforeEach
    void setUp() {
        llm = new ScriptedLlmClient();
        ObjectMapper mapper = new ObjectMapper();
        OrderLookupTools orders = new OrderLookupTools(orderService, mapper);
        ReturnRequestTools returns = new ReturnRequestTools(orders, returnRequestService, mapper);
        chat = new ChatService(llm, new ChatTools(orders, returns));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loggedInUserSeesOnlyTheirRecentOrderStatus() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.PAID, null)));
        llm.enqueue(tool("list_my_orders", "{\"userId\":\"bob\"}"));
        llm.enqueue(text("주문 ORD-10은 결제 완료입니다."));

        String reply = chat.reply(new ChatRequest("최근 주문 상태 알려줘", null));

        assertEquals("주문 ORD-10은 결제 완료입니다.", reply);
        String toolResult = llm.toolResult(1);
        assertTrue(toolResult.contains("\"orderNumber\":\"ORD-10\""));
        assertTrue(toolResult.contains("\"statusLabel\":\"결제 완료\""));
        assertTrue(toolResult.contains("\"liveCarrierStatus\":null"));
        assertFalse(toolResult.contains("bob"));
        verify(orderService).getMyOrders("alice");
        verify(orderService, never()).getMyOrders("bob");
        verifyNoMutations();
    }

    @Test
    void shippingStatusUsesTrackingNumberWithoutCarrierLocation() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.SHIPPING, "TRACK-10")));
        llm.enqueue(tool("get_my_order", "{\"orderNumber\":\"ORD-10\",\"userId\":\"bob\"}"));
        llm.enqueue(text("운송장 TRACK-10이 등록되어 있습니다. 배송사 위치는 알 수 없습니다."));

        String reply = chat.reply(new ChatRequest("주문번호 ORD-10 배송이 어디까지 왔어?", null));

        assertEquals("운송장 TRACK-10이 등록되어 있습니다. 배송사 위치는 알 수 없습니다.", reply);
        String toolResult = llm.toolResult(1);
        assertTrue(toolResult.contains("\"trackingNumber\":\"TRACK-10\""));
        assertTrue(toolResult.contains("\"liveCarrierStatus\":null"));
        assertTrue(toolResult.contains("\"deliveredAt\":null"));
        verify(orderService).getMyOrders("alice");
        verifyNoMutations();
    }

    @Test
    void anotherUsersOrderNumberDoesNotLeak() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED, "TRACK-PRIVATE")));
        llm.enqueue(tool("get_my_order", "{\"orderNumber\":\"ORD-SECRET\",\"userId\":\"bob\"}"));
        llm.enqueue(text("해당 주문을 찾을 수 없습니다."));

        String reply = chat.reply(new ChatRequest("주문번호 ORD-SECRET 보여줘", null));

        assertEquals("해당 주문을 찾을 수 없습니다.", reply);
        String toolResult = llm.toolResult(1);
        assertTrue(toolResult.contains("\"found\":false"));
        assertFalse(toolResult.contains("ORD-SECRET"));
        assertFalse(toolResult.contains("TRACK-PRIVATE"));
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnPolicyDoesNotDecideASpecificOrder() {
        login("alice");
        llm.enqueue(tool("explain_return_policy", "{}"));
        llm.enqueue(text("배송완료 주문만 신청할 수 있습니다. 특정 주문은 따로 확인해야 합니다."));

        String reply = chat.reply(new ChatRequest("반품 정책 알려줘", null));

        assertEquals("배송완료 주문만 신청할 수 있습니다. 특정 주문은 따로 확인해야 합니다.", reply);
        String toolResult = llm.toolResult(1);
        assertTrue(toolResult.contains("\"eligibleOrderStatuses\":[\"DELIVERED\"]"));
        assertTrue(toolResult.contains("\"appliesToIndividualOrder\":false"));
        assertTrue(toolResult.contains("\"refundExecuted\":false"));
        assertFalse(toolResult.contains("7일"));
        verifyNoInteractions(orderService);
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void ineligibleReturnIsRejectedWithoutRefund() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.PAID, null)));
        org.mockito.Mockito.doThrow(new IllegalStateException("배송완료 주문만 반품 신청할 수 있습니다."))
                .when(returnRequestService).assertMemberCanRequest("alice", 10L);
        llm.enqueue(tool("check_return_eligibility", "{\"orderNumber\":\"ORD-10\"}"));
        llm.enqueue(text("결제 완료 상태라 반품 신청이 되지 않습니다."));

        String reply = chat.reply(new ChatRequest("ORD-10 반품하고 싶어", null));

        assertEquals("결제 완료 상태라 반품 신청이 되지 않습니다.", reply);
        assertTrue(llm.toolResult(1).contains("\"eligible\":false"));
        assertTrue(llm.toolResult(1).contains("배송완료 주문만 반품 신청할 수 있습니다."));
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoMutations();
    }

    @Test
    void confirmedReturnSubmitsOnlyAfterTheUserPhrase() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED, null)));
        when(returnRequestService.submitMemberReturn("alice", 10L, "사이즈가 맞지 않음"))
                .thenReturn(order("ORD-10", 10L, OrderStatus.RETURN_REQUESTED, null));
        llm.enqueue(tool("check_return_eligibility", "{\"orderNumber\":\"ORD-10\",\"userId\":\"bob\"}"));
        llm.enqueue(text("ORD-10 셔츠를 사이즈가 맞지 않음으로 반품합니다. 반품 신청 확인이라고 답해 주세요."));

        String summary = chat.reply(new ChatRequest("ORD-10 반품하고 싶어. 사유는 사이즈가 맞지 않음", null));
        assertTrue(summary.contains("반품 신청 확인"));
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        llm.enqueue(tool("submit_return_request",
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"사이즈가 맞지 않음\",\"confirmed\":true,\"userId\":\"bob\"}"));
        llm.enqueue(text("반품 신청을 접수했습니다. 환불은 진행되지 않았습니다."));

        String accepted = chat.reply(new ChatRequest(
                "반품 신청 확인",
                List.of(
                        new ChatTurn("user", "ORD-10 반품하고 싶어. 사유는 사이즈가 맞지 않음"),
                        new ChatTurn("assistant", summary))));

        assertEquals("반품 신청을 접수했습니다. 환불은 진행되지 않았습니다.", accepted);
        assertTrue(llm.lastToolResult().contains("\"accepted\":true"));
        assertTrue(llm.lastToolResult().contains("\"refundExecuted\":false"));
        verify(returnRequestService).submitMemberReturn("alice", 10L, "사이즈가 맞지 않음");
        verify(returnRequestService, never()).submitMemberReturn("bob", 10L, "사이즈가 맞지 않음");
        verifyNoMutations();
    }

    @Test
    void returnSubmitWithoutUserConfirmationDoesNotCreateARequest() {
        login("alice");
        llm.enqueue(tool("submit_return_request",
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"변심\",\"confirmed\":true,\"userId\":\"bob\"}"));
        llm.enqueue(text("확인 전에는 접수할 수 없습니다."));

        String reply = chat.reply(new ChatRequest("지금 바로 반품 접수해 줘", null));

        assertEquals("확인 전에는 접수할 수 없습니다.", reply);
        assertTrue(llm.toolResult(1).contains("사용자 확인 전에는 반품을 접수할 수 없습니다."));
        verifyNoInteractions(orderService);
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void providerTimeoutDoesNotCallOrderTools() {
        login("alice");
        llm.enqueue(new LlmException("챗봇 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."));

        LlmException error = assertThrows(
                LlmException.class,
                () -> chat.reply(new ChatRequest("최근 주문 알려줘", null)));

        assertEquals("챗봇 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", error.getMessage());
        verifyNoInteractions(orderService);
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void refundRequestCannotInvokePaymentTools() {
        login("alice");
        llm.enqueue(tool("cancel_payment", "{\"orderNumber\":\"ORD-10\",\"userId\":\"bob\"}"));
        llm.enqueue(text("환불과 결제 취소는 진행하지 않습니다. 마이페이지에서 직접 진행해 주세요."));

        String reply = chat.reply(new ChatRequest("결제 취소하고 환불해 줘", null));

        assertEquals("환불과 결제 취소는 진행하지 않습니다. 마이페이지에서 직접 진행해 주세요.", reply);
        assertTrue(llm.toolResult(1).contains("지원하지 않는 조회입니다."));
        assertTrue(llm.systemPrompt().contains("마이페이지에서 직접 진행하라고 안내합니다."));
        String allowed = String.valueOf(llm.tools.get(0));
        assertTrue(allowed.contains("list_my_orders"));
        assertTrue(allowed.contains("get_my_order"));
        assertTrue(allowed.contains("explain_return_policy"));
        assertTrue(allowed.contains("check_return_eligibility"));
        assertTrue(allowed.contains("submit_return_request"));
        assertFalse(allowed.contains("cancel_payment"));
        assertFalse(allowed.contains("refund"));
        verifyNoInteractions(orderService);
        verifyNoMutations();
    }

    @Test
    void anonymousOrderQuestionDoesNotCallTheProvider() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> chat.reply(new ChatRequest("최근 주문 알려줘", null)));

        assertEquals("로그인 후 이용해 주세요.", error.getMessage());
        assertTrue(llm.requests.isEmpty());
        verifyNoInteractions(orderService);
        verifyNoInteractions(returnRequestService);
    }

    private void verifyNoMutations() {
        verify(returnRequestService, never()).mockComplete(org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).approve(org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).reject(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).receive(org.mockito.ArgumentMatchers.any());
        verify(orderService, never()).cancelMyOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static LlmCompletion tool(String name, String arguments) {
        return new LlmCompletion(null, List.of(new LlmToolCall("call_1", name, arguments)));
    }

    private static LlmCompletion text(String content) {
        return new LlmCompletion(content, List.of());
    }

    private static Orders order(String merchantUid, Long id, OrderStatus status, String trackingNumber) {
        Orders order = new Orders();
        order.setId(id);
        order.setOrderMerchantUid(merchantUid);
        order.setStatus(status);
        order.setTrackingNumber(trackingNumber);
        Product product = new Product();
        product.setName("셔츠");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        order.setOrderItems(List.of(item));
        return order;
    }

    private static void login(String userKey) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userKey, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static final class ScriptedLlmClient implements LlmClient {
        private final Deque<Object> script = new ArrayDeque<>();
        private final List<List<Map<String, Object>>> requests = new ArrayList<>();
        private final List<List<Map<String, Object>>> tools = new ArrayList<>();

        void enqueue(Object step) {
            script.add(step);
        }

        String lastToolResult() {
            for (int requestIndex = requests.size() - 1; requestIndex >= 0; requestIndex--) {
                String content = toolResult(requestIndex);
                if (!content.isEmpty()) {
                    return content;
                }
            }
            return "";
        }

        String toolResult(int requestIndex) {
            List<Map<String, Object>> messages = requests.get(requestIndex);
            for (int index = messages.size() - 1; index >= 0; index--) {
                if ("tool".equals(messages.get(index).get("role"))) {
                    return String.valueOf(messages.get(index).get("content"));
                }
            }
            return "";
        }

        String systemPrompt() {
            return String.valueOf(requests.get(0).get(0).get("content"));
        }

        @Override
        public String complete(List<ChatTurn> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmCompletion completeWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            requests.add(messages);
            this.tools.add(tools);
            Object step = script.removeFirst();
            if (step instanceof RuntimeException exception) {
                throw exception;
            }
            return (LlmCompletion) step;
        }
    }
}
