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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLookupToolsTest {

    @Mock
    private OrderService orderService;

    private OrderLookupTools tools;

    @BeforeEach
    void setUp() {
        tools = new OrderLookupTools(orderService, new ObjectMapper());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void definitionsAreReadOnlyOrderLookups() throws Exception {
        String schema = new ObjectMapper().writeValueAsString(tools.definitions());

        assertTrue(schema.contains(OrderLookupTools.LIST_MY_ORDERS));
        assertTrue(schema.contains(OrderLookupTools.GET_MY_ORDER));
        assertFalse(schema.contains("refund"));
        assertFalse(schema.contains("return"));
        assertFalse(schema.contains("cancel"));
    }

    @Test
    void listsOnlyTheAuthenticatedUsersOrders() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(
                order("ORD-10", 10L, OrderStatus.PREPARING, null, "셔츠")));

        String result = tools.execute(OrderLookupTools.LIST_MY_ORDERS, "{\"userId\":\"bob\"}");

        assertTrue(result.contains("\"orderNumber\":\"ORD-10\""));
        assertTrue(result.contains("\"statusLabel\":\"배송 준비\""));
        assertTrue(result.contains("\"beforeShipping\":true"));
        assertTrue(result.contains("\"trackingNumber\":null"));
        assertTrue(result.contains("\"liveCarrierStatus\":null"));
        assertTrue(result.contains("\"deliveredAt\":null"));
        assertTrue(result.contains("\"name\":\"셔츠\""));
        assertFalse(result.contains("bob"));
        assertFalse(result.contains("partnerSettlement"));
        verify(orderService).getMyOrders("alice");
        verify(orderService, never()).getMyOrders("bob");
    }

    @Test
    void emptyOrderListSaysThereAreNoOrders() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of());

        String result = tools.execute(OrderLookupTools.LIST_MY_ORDERS, "{}");

        assertTrue(result.contains("\"orders\":[]"));
        assertTrue(result.contains("조회된 주문이 없습니다."));
    }

    @Test
    void ownedOrderIncludesTrackingNumberWithoutCarrierStatus() {
        login("alice");
        Orders mine = order("ORD-10", 10L, OrderStatus.SHIPPING, "TRACK-10", "셔츠");
        mine.setNetAmount(new BigDecimal("12000"));
        when(orderService.getMyOrders("alice")).thenReturn(List.of(mine));

        String result = tools.execute(
                OrderLookupTools.GET_MY_ORDER,
                "{\"orderNumber\":\"10\",\"userId\":\"mallory\",\"customerId\":\"mallory\"}");

        assertTrue(result.contains("\"found\":true"));
        assertTrue(result.contains("\"orderNumber\":\"ORD-10\""));
        assertTrue(result.contains("\"trackingNumber\":\"TRACK-10\""));
        assertTrue(result.contains("\"statusLabel\":\"배송 중\""));
        assertTrue(result.contains("\"beforeShipping\":false"));
        assertTrue(result.contains("\"liveCarrierStatus\":null"));
        assertTrue(result.contains("\"deliveredAt\":null"));
        assertFalse(result.contains("mallory"));
        verify(orderService).getMyOrders("alice");
        verify(orderService, never()).getMyOrders("mallory");
    }

    @Test
    void orderOwnedBySomeoneElseIsIndistinguishableFromMissing() {
        login("alice");
        when(orderService.getMyOrders("alice")).thenReturn(List.of(
                order("ORD-10", 10L, OrderStatus.PAID, "TRACK-PRIVATE", "내옷")));

        String result = tools.execute(
                OrderLookupTools.GET_MY_ORDER,
                "{\"orderNumber\":\"ORD-SECRET\",\"userId\":\"bob\"}");

        assertTrue(result.contains("\"found\":false"));
        assertTrue(result.contains("해당 주문을 찾을 수 없습니다."));
        assertFalse(result.contains("ORD-SECRET"));
        assertFalse(result.contains("TRACK-PRIVATE"));
        assertFalse(result.contains("내옷"));
        assertFalse(result.contains("bob"));
        verify(orderService).getMyOrders("alice");
        verify(orderService, never()).getAllOrdersForAdmin();
    }

    @Test
    void anonymousAndBadArgumentsDoNotTouchOrders() {
        String anonymous = tools.execute(OrderLookupTools.LIST_MY_ORDERS, "{}");
        assertTrue(anonymous.contains("로그인 후 이용해 주세요."));

        login("alice");
        String malformed = tools.execute(OrderLookupTools.GET_MY_ORDER, "not-json");
        String missingNumber = tools.execute(OrderLookupTools.GET_MY_ORDER, "{\"userId\":\"bob\"}");
        String unknown = tools.execute("cancel_order", "{}");

        assertTrue(malformed.contains("주문번호를 확인할 수 없습니다."));
        assertTrue(missingNumber.contains("주문번호를 확인할 수 없습니다."));
        assertTrue(unknown.contains("지원하지 않는 조회입니다."));
        verifyNoInteractions(orderService);
    }

    @Test
    void lookupFailuresDoNotExposeInternalText() {
        login("alice");
        when(orderService.getMyOrders("alice"))
                .thenThrow(new IllegalArgumentException("회원 정보를 찾을 수 없습니다. secret-detail"))
                .thenThrow(new RuntimeException("db-password-secret"));

        String missingMember = tools.execute(OrderLookupTools.LIST_MY_ORDERS, "{}");
        String broken = tools.execute(OrderLookupTools.GET_MY_ORDER, "{\"orderNumber\":\"ORD-1\"}");

        assertTrue(missingMember.contains("회원 주문을 확인할 수 없습니다."));
        assertFalse(missingMember.contains("secret-detail"));
        assertTrue(broken.contains("주문 조회 중 문제가 생겼습니다."));
        assertFalse(broken.contains("db-password-secret"));
    }

    @Test
    void listKeepsOnlyTheNewestLimitedOrders() {
        login("alice");
        List<Orders> orders = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            orders.add(order("ORD-" + index, (long) index, OrderStatus.PAID, null, "상품" + index));
        }
        when(orderService.getMyOrders("alice")).thenReturn(orders);

        String result = tools.execute(OrderLookupTools.LIST_MY_ORDERS, "{}");

        assertTrue(result.contains("\"total\":11"));
        assertTrue(result.contains("\"truncated\":true"));
        assertTrue(result.contains("ORD-0"));
        assertTrue(result.contains("ORD-9"));
        assertFalse(result.contains("ORD-10"));
    }

    private static Orders order(String merchantUid, Long id, OrderStatus status, String trackingNumber, String productName) {
        Orders order = new Orders();
        order.setId(id);
        order.setOrderMerchantUid(merchantUid);
        order.setStatus(status);
        order.setTrackingNumber(trackingNumber);
        order.setOrderDate(LocalDateTime.of(2026, 10, 1, 9, 0));
        Product product = new Product();
        product.setName(productName);
        OrderItem item = new OrderItem();
        item.setCount(1);
        item.setProduct(product);
        order.setOrderItems(List.of(item));
        return order;
    }

    private static void login(String userKey) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userKey, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
