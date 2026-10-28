package com.example.ecommerce.service;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestToolsTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ReturnRequestService returnRequestService;

    private ReturnRequestTools tools;

    @BeforeEach
    void setUp() {
        tools = new ReturnRequestTools(
                new OrderLookupTools(orderService, new ObjectMapper()),
                returnRequestService,
                new ObjectMapper());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void policyComesFromCodeAndDoesNotDecideAnOrder() throws Exception {
        login("alice");

        String result = tools.execute(ReturnRequestTools.EXPLAIN_RETURN_POLICY, "{}", "반품 정책 알려줘");

        assertTrue(result.contains("\"eligibleOrderStatuses\":[\"DELIVERED\"]"));
        assertTrue(result.contains("\"appliesToIndividualOrder\":false"));
        assertTrue(result.contains("\"refundExecuted\":false"));
        assertTrue(result.contains("\"paymentCancelExecuted\":false"));
        assertTrue(result.contains("반품 가능 일수"));
        assertFalse(result.contains("7일"));
        assertFalse(result.contains("14일"));
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void eligibilityCheckDoesNotCreateARequest() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED)));

        String allowed = tools.execute(
                ReturnRequestTools.CHECK_RETURN_ELIGIBILITY,
                "{\"orderNumber\":\"ORD-10\",\"userId\":\"bob\"}",
                "이 주문 반품돼?");

        assertTrue(allowed.contains("\"eligible\":true"));
        assertTrue(allowed.contains("\"accepted\":false"));
        assertTrue(allowed.contains("셔츠"));
        assertFalse(allowed.contains("bob"));
        verify(returnRequestService).assertMemberCanRequest("alice", 10L);
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(orderService, never()).getMyOrders("bob");
    }

    @Test
    void ineligibleOrderIsReportedWithoutSubmission() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.SHIPPING)));
        org.mockito.Mockito.doThrow(new IllegalStateException("배송완료 주문만 반품 신청할 수 있습니다."))
                .when(returnRequestService).assertMemberCanRequest("alice", 10L);

        String result = tools.execute(
                ReturnRequestTools.CHECK_RETURN_ELIGIBILITY,
                "{\"orderNumber\":\"ORD-10\"}",
                "반품돼?");

        assertTrue(result.contains("\"eligible\":false"));
        assertTrue(result.contains("배송완료 주문만 반품 신청할 수 있습니다."));
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmedOwnedOrderSubmitsWithoutRefund() {
        login("alice");
        Orders saved = order("ORD-10", 10L, OrderStatus.RETURN_REQUESTED);
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED)));
        when(returnRequestService.submitMemberReturn("alice", 10L, "사이즈가 맞지 않음")).thenReturn(saved);

        String result = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"10\",\"reason\":\"사이즈가 맞지 않음\",\"confirmed\":true,\"userId\":\"bob\"}",
                "반품 신청 확인");

        assertTrue(result.contains("\"accepted\":true"));
        assertTrue(result.contains("\"orderStatus\":\"RETURN_REQUESTED\""));
        assertTrue(result.contains("\"returnStatus\":\"REQUESTED\""));
        assertTrue(result.contains("\"refundExecuted\":false"));
        assertTrue(result.contains("\"paymentCancelExecuted\":false"));
        assertTrue(result.contains("\"stockChanged\":false"));
        assertFalse(result.contains("bob"));
        verify(returnRequestService).submitMemberReturn("alice", 10L, "사이즈가 맞지 않음");
        verify(returnRequestService, never()).mockComplete(org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).approve(org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).reject(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(returnRequestService, never()).receive(org.mockito.ArgumentMatchers.any());
        verify(orderService, never()).cancelMyOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingAndOtherUsersOrdersAreRejectedTheSameWay() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED)));

        String missing = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"999\",\"reason\":\"변심\",\"confirmed\":true}",
                "반품 신청 확인");
        String other = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"ORD-SECRET\",\"reason\":\"변심\",\"confirmed\":true,\"userId\":\"bob\"}",
                "반품 신청 확인");

        assertTrue(missing.contains("해당 주문을 찾을 수 없습니다."));
        assertTrue(other.contains("해당 주문을 찾을 수 없습니다."));
        assertFalse(missing.contains("999"));
        assertFalse(other.contains("ORD-SECRET"));
        assertFalse(other.contains("bob"));
        verify(returnRequestService, never()).submitMemberReturn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void serverRejectsAfterConfirmationWhenTheOrderCannotBeReturned() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.PAID)));
        when(returnRequestService.submitMemberReturn("alice", 10L, "변심"))
                .thenThrow(new IllegalStateException("배송완료 주문만 반품 신청할 수 있습니다."));

        String result = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"변심\",\"confirmed\":true}",
                "반품 신청 확인");

        assertTrue(result.contains("\"accepted\":false"));
        assertTrue(result.contains("배송완료 주문만 반품 신청할 수 있습니다."));
        assertTrue(result.contains("\"refundExecuted\":false"));
        verify(returnRequestService, never()).mockComplete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateRequestIsRejectedByTheServer() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(order("ORD-10", 10L, OrderStatus.DELIVERED)));
        when(returnRequestService.submitMemberReturn("alice", 10L, "변심"))
                .thenThrow(new IllegalStateException("이미 진행 중인 반품 신청이 있습니다."));

        String result = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"변심\",\"confirmed\":true}",
                "반품 신청 확인");

        assertTrue(result.contains("이미 진행 중인 반품 신청이 있습니다."));
        assertTrue(result.contains("\"refundExecuted\":false"));
    }

    @Test
    void confirmationFlagOrPhraseAloneDoesNotSubmit() {
        login("alice");

        String noPhrase = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"변심\",\"confirmed\":true}",
                "네 신청해 주세요");
        String noFlag = tools.execute(
                ReturnRequestTools.SUBMIT_RETURN_REQUEST,
                "{\"orderNumber\":\"ORD-10\",\"reason\":\"변심\",\"confirmed\":false}",
                "반품 신청 확인");

        assertTrue(noPhrase.contains("사용자 확인 전에는 반품을 접수할 수 없습니다."));
        assertTrue(noFlag.contains("사용자 확인 전에는 반품을 접수할 수 없습니다."));
        verifyNoInteractions(orderService);
        verifyNoInteractions(returnRequestService);
    }

    private static Orders order(String merchantUid, Long id, OrderStatus status) {
        Orders order = new Orders();
        order.setId(id);
        order.setOrderMerchantUid(merchantUid);
        order.setStatus(status);
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
}
