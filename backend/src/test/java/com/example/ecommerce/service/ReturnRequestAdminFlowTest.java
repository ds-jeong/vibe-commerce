package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestAdminFlowTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ReturnRequestService returnRequestService;

    private Orders order;
    private ReturnRequest request;
    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(5L);
        product.setStockQuantity(3);

        OrderItem item = new OrderItem();
        item.setCount(2);
        item.setProduct(product);

        order = new Orders();
        order.setId(10L);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setOrderItems(List.of(item));

        request = new ReturnRequest();
        request.setId(99L);
        request.setOrder(order);
        request.setStatus(ReturnStatus.REQUESTED);
    }

    @Test
    void approve_fromRequested() {
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(request));
        when(returnRequestRepository.updateStatusIfMatch(99L, ReturnStatus.REQUESTED, ReturnStatus.APPROVED))
                .thenReturn(1);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Map<String, Object> row = returnRequestService.approve(10L);

        assertEquals("RETURN_REQUESTED", row.get("status"));
        assertEquals("APPROVED", row.get("returnStatus"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void reject_restoresDeliveredWithoutStockChange() {
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(request));
        when(returnRequestRepository.updateStatusIfMatch(99L, ReturnStatus.REQUESTED, ReturnStatus.REJECTED))
                .thenReturn(1);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Map<String, Object> row = returnRequestService.reject(10L, "불량 아님");

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals("REJECTED", row.get("returnStatus"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void receive_fromApprovedKeepsOrderStatus() {
        request.setStatus(ReturnStatus.APPROVED);
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(request));
        when(returnRequestRepository.updateStatusIfMatch(99L, ReturnStatus.APPROVED, ReturnStatus.RECEIVED))
                .thenReturn(1);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Map<String, Object> row = returnRequestService.receive(10L);

        assertEquals("RETURN_REQUESTED", row.get("status"));
        assertEquals("RECEIVED", row.get("returnStatus"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void mockComplete_restoresStockOnce() {
        request.setStatus(ReturnStatus.RECEIVED);
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(request));
        when(returnRequestRepository.updateStatusIfMatch(99L, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED))
                .thenReturn(1);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Map<String, Object> row = returnRequestService.mockComplete(10L);

        assertEquals("REFUNDED", row.get("status"));
        assertEquals("COMPLETED", row.get("returnStatus"));
        assertEquals(5, product.getStockQuantity());
        verify(productRepository).save(product);
    }

    @Test
    void mockComplete_rejectsWhenTransitionAlreadyDone() {
        request.setStatus(ReturnStatus.RECEIVED);
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(request));
        when(returnRequestRepository.updateStatusIfMatch(99L, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED))
                .thenReturn(0);

        assertThrows(IllegalStateException.class, () -> returnRequestService.mockComplete(10L));
        verify(productRepository, never()).save(any());
        assertEquals(3, product.getStockQuantity());
    }

    @Test
    void receive_rejectsRequested() {
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> returnRequestService.receive(10L));
    }

    @Test
    void doesNotOverwriteRefundedOrder() {
        order.setStatus(OrderStatus.REFUNDED);
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> returnRequestService.approve(10L));
        assertTrue(ex.getMessage().contains("신규 반품 처리"));
    }

    @Test
    void listIncludesLegacyWithoutDuplicatingReturnRequest() {
        when(returnRequestRepository.findAllWithOrderItems()).thenReturn(List.of(request));
        when(ordersRepository.findLegacyReturnRequestedWithoutReturnRequest(OrderStatus.RETURN_REQUESTED))
                .thenReturn(List.of());

        List<Map<String, Object>> rows = returnRequestService.listAdminReturns();

        assertEquals(1, rows.size());
        assertEquals("RETURN_REQUEST", rows.get(0).get("source"));
    }

    @Test
    void listAddsLegacyOrders() {
        Orders legacy = new Orders();
        legacy.setId(11L);
        legacy.setStatus(OrderStatus.RETURN_REQUESTED);
        when(returnRequestRepository.findAllWithOrderItems()).thenReturn(List.of());
        when(ordersRepository.findLegacyReturnRequestedWithoutReturnRequest(OrderStatus.RETURN_REQUESTED))
                .thenReturn(List.of(legacy));

        List<Map<String, Object>> rows = returnRequestService.listAdminReturns();

        assertEquals(1, rows.size());
        assertEquals("LEGACY_ORDER", rows.get(0).get("source"));
        assertEquals(true, rows.get(0).get("legacyIncomplete"));
        assertEquals("REQUESTED", rows.get(0).get("returnStatus"));
    }
}
