package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.SaleStatus;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSaleValidationTest {

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortOneRefundService portOneRefundService;
    @Mock
    private SettlementLedgerService settlementLedgerService;
    @Mock
    private ReturnRequestService returnRequestService;
    @Mock
    private EntityManager entityManager;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                ordersRepository,
                paymentRepository,
                passwordEncoder,
                productRepository,
                userRepository,
                portOneRefundService,
                settlementLedgerService,
                returnRequestService,
                entityManager);
    }

    @Test
    void createOrderUsesServerPriceAndIgnoresClientPrice() {
        Product product = product(1L, SaleStatus.ON_SALE, 5, "5000");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(ordersRepository.save(any(Orders.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> placed = orderService.createOrder(orderParams(1, 2, "1"), null);

        assertEquals(new BigDecimal("10000"), placed.get("goodsAmount"));
        assertEquals(new BigDecimal("5000"), product.getPrice());
    }

    @Test
    void nullSaleStatusIsTreatedAsOnSale() {
        Product product = product(1L, null, 3, "1000");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(ordersRepository.save(any(Orders.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> placed = orderService.createOrder(orderParams(1, 1, "1"), null);

        assertEquals("SUCCESS", placed.get("status"));
    }

    @Test
    void stoppedProductIsRejected() {
        Product product = product(1L, SaleStatus.STOPPED, 5, "1000");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(orderParams(1, 1, "1000"), null));

        assertEquals("판매중지된 상품은 주문할 수 없습니다.", ex.getMessage());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void insufficientAndInvalidQuantityAreRejected() {
        Product product = product(1L, SaleStatus.ON_SALE, 2, "1000");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        IllegalArgumentException shortage = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(orderParams(1, 3, "1000"), null));
        assertEquals("재고가 부족합니다.", shortage.getMessage());

        IllegalArgumentException quantity = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(orderParams(1, 0, "1000"), null));
        assertEquals("주문 수량이 올바르지 않습니다.", quantity.getMessage());

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(orderParams(99, 1, "1000"), null));
        assertTrue(missing.getMessage().contains("존재하지 않는 상품"));
    }

    @Test
    void aggregatedQuantityCannotExceedStock() {
        Product product = product(1L, SaleStatus.ON_SALE, 5, "1000");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        Map<String, Object> params = baseParams();
        params.put("items", List.of(line(1, 3), line(1, 3)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(params, null));

        assertEquals("재고가 부족합니다.", ex.getMessage());
    }

    @Test
    void paymentDeductsStoppedProductWhenStockRemains() {
        Product product = product(7L, SaleStatus.STOPPED, 4, "1000");
        Orders order = payableOrder(product, 1, "1000");
        when(ordersRepository.findWithItemsByOrderMerchantUid("VIBE-1")).thenReturn(Optional.of(order));
        when(productRepository.decrementStockIfEnough(7L, 1)).thenAnswer(invocation -> {
            product.setStockQuantity(product.getStockQuantity() - 1);
            return 1;
        });

        orderService.verifyAndCompletePayment(verifyParams("1000"));

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(3, product.getStockQuantity());
    }

    @Test
    void duplicatePaymentDoesNotDeductAgain() {
        Product product = product(7L, SaleStatus.ON_SALE, 4, "1000");
        Orders order = payableOrder(product, 1, "1000");
        order.setStatus(OrderStatus.PAID);
        when(ordersRepository.findWithItemsByOrderMerchantUid("VIBE-1")).thenReturn(Optional.of(order));

        orderService.verifyAndCompletePayment(verifyParams("1000"));

        assertEquals(4, product.getStockQuantity());
        verify(paymentRepository, never()).save(any());
        verify(productRepository, never()).decrementStockIfEnough(any(), anyInt());
    }

    @Test
    void paymentRefusesToClampInsufficientStock() {
        Product product = product(7L, SaleStatus.ON_SALE, 1, "1000");
        Orders order = payableOrder(product, 2, "1000");
        when(ordersRepository.findWithItemsByOrderMerchantUid("VIBE-1")).thenReturn(Optional.of(order));
        when(productRepository.decrementStockIfEnough(eq(7L), eq(2))).thenReturn(0);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> orderService.verifyAndCompletePayment(verifyParams("1000")));

        assertEquals("재고가 부족하여 결제를 완료할 수 없습니다.", ex.getMessage());
        assertEquals(OrderStatus.ORDERED, order.getStatus());
        assertEquals(1, product.getStockQuantity());
        verify(paymentRepository, never()).save(any());
    }

    private Map<String, Object> orderParams(long productId, int quantity, String clientPrice) {
        Map<String, Object> params = baseParams();
        params.put("price", clientPrice);
        params.put("items", List.of(line(productId, quantity)));
        return params;
    }

    private Map<String, Object> baseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("ordererName", "홍길동");
        params.put("phoneNumber", "01012345678");
        return params;
    }

    private Map<String, Object> line(long productId, int quantity) {
        Map<String, Object> line = new HashMap<>();
        line.put("id", productId);
        line.put("quantity", quantity);
        return line;
    }

    private Map<String, Object> verifyParams(String amount) {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantUid", "VIBE-1");
        params.put("totalAmount", amount);
        params.put("txId", "tx-1");
        params.put("paymentId", "pay-1");
        return params;
    }

    private Product product(Long id, SaleStatus status, int stock, String price) {
        Product product = new Product();
        product.setId(id);
        product.setSaleStatus(status);
        product.setStockQuantity(stock);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private Orders payableOrder(Product product, int count, String amount) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setCount(count);
        Orders order = new Orders();
        order.setOrderMerchantUid("VIBE-1");
        order.setStatus(OrderStatus.ORDERED);
        order.setNetAmount(new BigDecimal(amount));
        order.setOrderItems(List.of(item));
        return order;
    }
}
