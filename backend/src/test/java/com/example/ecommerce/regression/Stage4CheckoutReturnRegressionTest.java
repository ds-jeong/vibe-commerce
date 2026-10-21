package com.example.ecommerce.regression;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.SettlementRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PortOneRefundService;
import com.example.ecommerce.service.ReturnRequestService;
import com.example.ecommerce.service.SettlementLedgerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Stage4CheckoutReturnRegressionTest {

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PortOneRefundService portOneRefundService;
    @Mock
    private EntityManager entityManager;

    private Stage4MemoryCatalog memory;
    private OrderService orderService;
    private ReturnRequestService returnRequestService;

    @BeforeEach
    void setUp() {
        memory = new Stage4MemoryCatalog();
        memory.stub(
                ordersRepository,
                returnRequestRepository,
                productRepository,
                userRepository,
                paymentRepository,
                settlementRepository);
        memory.addUser(1L, "member1");
        memory.addProduct(20L, "어반 캔버스 백팩", 10, new BigDecimal("40000"));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        returnRequestService = new ReturnRequestService(
                userRepository, ordersRepository, returnRequestRepository, productRepository);
        orderService = new OrderService(
                ordersRepository,
                paymentRepository,
                passwordEncoder,
                productRepository,
                userRepository,
                portOneRefundService,
                new SettlementLedgerService(settlementRepository),
                returnRequestService,
                entityManager);
    }

    @Test
    void paidDeliveredReturnRejectRetryApproveReceiveMockComplete() {
        Map<String, Object> placed = placePaidOrder("member1");
        String merchantUid = String.valueOf(placed.get("merchantUid"));
        Orders order = memory.ordersByMerchant.get(merchantUid);
        Long orderId = order.getId();

        assertEquals("SUCCESS", placed.get("status"));
        assertEquals(new BigDecimal("3000"), placed.get("deliveryFee"));
        assertEquals(new BigDecimal("40000"), placed.get("goodsAmount"));
        assertEquals(0, new BigDecimal("43000").compareTo((BigDecimal) placed.get("amount")));
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(9, memory.products.get(20L).getStockQuantity());
        assertEquals(1, memory.payments.size());
        assertEquals(1, memory.settlements.size());
        assertEquals(0, new BigDecimal("43000").compareTo(memory.settlements.get(orderId).getTotalSalesAmount()));

        orderService.updateAdminOrderStatus(orderId, Map.of("status", "PREPARING"));
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "SHIPPING", "trackingNumber", "1234567890123"));
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "DELIVERED"));
        assertEquals(OrderStatus.DELIVERED, memory.orders.get(orderId).getStatus());

        IllegalStateException cancelBlocked = assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelMyOrder("member1", orderId));
        assertTrue(cancelBlocked.getMessage().contains("즉시 취소"));

        Map<String, Object> requested = orderService.requestReturn("member1", orderId);
        assertEquals("RETURN_REQUESTED", requested.get("status"));

        Map<String, Object> rejected = returnRequestService.reject(orderId, "검수 반려");
        assertEquals("REJECTED", rejected.get("returnStatus"));
        assertEquals("DELIVERED", rejected.get("status"));
        assertEquals("검수 반려", rejected.get("reason"));

        Map<String, Object> retried = orderService.requestReturn("member1", orderId);
        assertEquals("RETURN_REQUESTED", retried.get("status"));
        IllegalStateException duplicateWhileRequested = assertThrows(
                IllegalStateException.class,
                () -> orderService.requestReturn("member1", orderId));
        assertEquals("배송완료 주문만 반품 신청할 수 있습니다.", duplicateWhileRequested.getMessage());

        Map<String, Object> approved = returnRequestService.approve(orderId);
        assertEquals("APPROVED", approved.get("returnStatus"));
        assertEquals("RETURN_REQUESTED", approved.get("status"));

        Map<String, Object> received = returnRequestService.receive(orderId);
        assertEquals("RECEIVED", received.get("returnStatus"));

        Map<String, Object> completed = returnRequestService.mockComplete(orderId);
        assertEquals("COMPLETED", completed.get("returnStatus"));
        assertEquals("REFUNDED", completed.get("status"));
        assertEquals(10, memory.products.get(20L).getStockQuantity());
        assertEquals(false, completed.get("legacyIncomplete"));
        assertEquals("RETURN_REQUEST", completed.get("source"));

        verifyNoInteractions(portOneRefundService);
        assertEquals(1, memory.payments.size());
        assertEquals(1, memory.settlements.size());

        assertThrows(IllegalStateException.class, () -> returnRequestService.mockComplete(orderId));
        assertEquals(10, memory.products.get(20L).getStockQuantity());

        IllegalStateException pgPathBlocked = assertThrows(
                IllegalStateException.class,
                () -> orderService.processReturnRefundWithSafety(orderId));
        assertTrue(pgPathBlocked.getMessage().contains("반품 신청 건만"));
        verify(portOneRefundService, never()).cancelPaymentForReturnRefund(any(), any());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shipping = (List<Map<String, Object>>) orderService.searchAdminOrders(
                null, null, null, null, null, "shipping");
        assertTrue(shipping.stream().noneMatch(row -> orderId.equals(row.get("id"))));
    }

    @Test
    void immediateCancelRestoresStockAndUsesCancelPaymentNotReturnRefundApi() {
        placePaidOrder("member1");
        Orders order = memory.orders.values().iterator().next();
        assertEquals(9, memory.products.get(20L).getStockQuantity());

        Map<String, Object> cancelled = orderService.cancelMyOrder("member1", order.getId());
        assertEquals("CANCELLED", cancelled.get("status"));
        assertEquals(10, memory.products.get(20L).getStockQuantity());
        verify(portOneRefundService).cancelPayment(any(), eq("고객 즉시 주문취소"));
        verify(portOneRefundService, never()).cancelPaymentForReturnRefund(any(), any());
    }

    @Test
    void paymentAmountMismatchCancelsWithoutConsumingStock() {
        Map<String, Object> placed = placePaidOrderParams("member1", false);
        String merchantUid = String.valueOf(placed.get("merchantUid"));
        Map<String, Object> verify = new HashMap<>();
        verify.put("merchantUid", merchantUid);
        verify.put("totalAmount", "1");
        verify.put("txId", "tx-bad");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> orderService.verifyAndCompletePayment(verify));
        assertTrue(ex.getMessage().contains("결제 금액 불일치"));
        assertEquals(OrderStatus.CANCELLED, memory.ordersByMerchant.get(merchantUid).getStatus());
        assertEquals(10, memory.products.get(20L).getStockQuantity());
        assertTrue(memory.payments.isEmpty());
        assertTrue(memory.settlements.isEmpty());
        verifyNoInteractions(portOneRefundService);
    }

    @Test
    void shippingRequiresTrackingNumber() {
        placePaidOrder("member1");
        Long orderId = memory.orders.values().iterator().next().getId();
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "PREPARING"));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateAdminOrderStatus(orderId, Map.of("status", "SHIPPING")));
        assertEquals("배송 시작 전 운송장 번호가 필요합니다.", ex.getMessage());
        assertEquals(OrderStatus.PREPARING, memory.orders.get(orderId).getStatus());
    }

    @Test
    void processReturnRefundWithSafetyCallsIsolatedPortOnePathAndRestoresStock() {
        Map<String, Object> placed = placePaidOrder("member1");
        Long orderId = memory.ordersByMerchant.get(String.valueOf(placed.get("merchantUid"))).getId();
        deliver(orderId);
        orderService.requestReturn("member1", orderId);
        when(portOneRefundService.cancelPaymentForReturnRefund(any(), any())).thenReturn(true);

        Map<String, Object> refunded = orderService.processReturnRefundWithSafety(orderId);

        assertEquals("RETURNED", refunded.get("status"));
        assertEquals(10, memory.products.get(20L).getStockQuantity());
        verify(portOneRefundService).cancelPaymentForReturnRefund(any(), eq("관리자 반품 승인 환불"));
        verify(portOneRefundService, never()).cancelPayment(any(), any());
    }

    @Test
    void createOrderContractKeysStayStable() {
        Map<String, Object> placed = placePaidOrderParams("member1", false);
        assertTrue(String.valueOf(placed.get("merchantUid")).startsWith("VIBE-"));
        assertEquals("SUCCESS", placed.get("status"));
        assertTrue(placed.containsKey("amount"));
        assertTrue(placed.containsKey("deliveryFee"));
        assertTrue(placed.containsKey("goodsAmount"));
        assertFalse(placed.containsKey("accessToken"));
    }

    private Map<String, Object> placePaidOrder(String username) {
        Map<String, Object> placed = placePaidOrderParams(username, true);
        return placed;
    }

    private Map<String, Object> placePaidOrderParams(String username, boolean pay) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", 20L);
        item.put("quantity", 1);
        Map<String, Object> params = new HashMap<>();
        params.put("ordererName", "테스터");
        params.put("phoneNumber", "01012345678");
        params.put("items", List.of(item));
        Map<String, Object> placed = orderService.createOrder(params, username);
        if (pay) {
            Map<String, Object> verify = new HashMap<>();
            verify.put("merchantUid", placed.get("merchantUid"));
            verify.put("totalAmount", placed.get("amount").toString());
            verify.put("txId", "tx-mock");
            verify.put("paymentId", "pay-mock");
            orderService.verifyAndCompletePayment(verify);
        }
        return placed;
    }

    private void deliver(Long orderId) {
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "PREPARING"));
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "SHIPPING", "trackingNumber", "1234567890123"));
        orderService.updateAdminOrderStatus(orderId, Map.of("status", "DELIVERED"));
    }
}
