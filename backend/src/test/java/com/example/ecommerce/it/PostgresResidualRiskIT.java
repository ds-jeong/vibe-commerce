package com.example.ecommerce.it;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.domain.Role;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.PortOneRefundService;
import com.example.ecommerce.service.ReturnRequestService;
import com.example.ecommerce.service.SettlementBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("itest")
class PostgresResidualRiskIT {

    @Autowired
    private ReturnRequestService returnRequestService;
    @Autowired
    private SettlementBatchService settlementBatchService;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private ReturnRequestRepository returnRequestRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PortOneRefundService portOneRefundService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private HttpClient httpClientProbe;
    private TransactionTemplate transactions;

    @BeforeEach
    void isolatePortOneHttp() throws Exception {
        transactions = new TransactionTemplate(transactionManager);
        httpClientProbe = mock(HttpClient.class);
        ReflectionTestUtils.setField(portOneRefundService, "httpClient", httpClientProbe);
        ReflectionTestUtils.setField(portOneRefundService, "apiSecret", "");
    }

    @Test
    void pessimisticWriteLockIsHeldUntilTransactionEnds() throws Exception {
        Fixture fixture = seedReceivedReturn(10, 2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> transactions.executeWithoutResult(status -> {
                ordersRepository.findByIdForUpdate(fixture.orderId)
                        .orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) {
                        fail("lock holder was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail(e);
                }
            }));

            assertTrue(locked.await(8, TimeUnit.SECONDS), "first transaction did not acquire lock");

            Future<String> waiter = pool.submit(() -> {
                TransactionTemplate nested = new TransactionTemplate(transactionManager);
                try {
                    nested.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL lock_timeout = '1000ms'");
                        ordersRepository.findByIdForUpdate(fixture.orderId).orElseThrow();
                    });
                    return "acquired";
                } catch (RuntimeException ex) {
                    return rootMessage(ex);
                }
            });

            String waiterResult = waiter.get(6, TimeUnit.SECONDS);
            assertTrue(
                    waiterResult.contains("canceling statement due to lock timeout")
                            || waiterResult.contains("lock timeout")
                            || waiterResult.contains("could not obtain lock")
                            || waiterResult.contains("PessimisticLock"),
                    "second FOR UPDATE should time out while first transaction holds the row, got: " + waiterResult);
            release.countDown();
            holder.get(6, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void mockCompleteStatusAndStockRollBackTogetherWhenStockSaveFails() {
        Fixture fixture = seedReceivedReturn(8, 3);
        String constraint = "itest_stock_fail_" + fixture.productId;
        jdbcTemplate.execute("ALTER TABLE products DROP CONSTRAINT IF EXISTS " + constraint);
        jdbcTemplate.execute(
                "ALTER TABLE products ADD CONSTRAINT " + constraint
                        + " CHECK (id <> " + fixture.productId + " OR stock_quantity <= 8)");
        try {
            assertThrows(RuntimeException.class, () -> returnRequestService.mockComplete(fixture.orderId));
            assertEquals("RECEIVED", jdbcTemplate.queryForObject(
                    "SELECT status FROM return_requests WHERE id = ?", String.class, fixture.returnRequestId));
            assertEquals("RETURN_REQUESTED", jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId));
            assertEquals(8, jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, fixture.productId));
            verifyNoPortOneHttp();
        } finally {
            jdbcTemplate.execute("ALTER TABLE products DROP CONSTRAINT IF EXISTS " + constraint);
        }
    }

    @Test
    void concurrentMockCompleteRestoresStockOnce() throws Exception {
        Fixture fixture = seedReceivedReturn(10, 2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        try {
            List<Future<?>> jobs = List.of(
                    pool.submit(() -> runMockComplete(fixture.orderId, start, successes, conflicts, unexpected)),
                    pool.submit(() -> runMockComplete(fixture.orderId, start, successes, conflicts, unexpected))
            );
            start.countDown();
            for (Future<?> job : jobs) {
                job.get(15, TimeUnit.SECONDS);
            }
            if (unexpected.get() != null) {
                fail(unexpected.get());
            }
            assertEquals(1, successes.get(), "exactly one mock-complete should commit");
            assertEquals(1, conflicts.get(), "the other request should see a state conflict");
            assertEquals(12, jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, fixture.productId));
            assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                    "SELECT status FROM return_requests WHERE id = ?", String.class, fixture.returnRequestId));
            assertEquals("REFUNDED", jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId));
            verifyNoPortOneHttp();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void settlementBatchUsesPgDateAndCountTypesAndIgnoresRefundedOrders() {
        LocalDate day = LocalDate.of(2026, 10, 15);
        jdbcTemplate.update("DELETE FROM refunds");
        jdbcTemplate.update("DELETE FROM return_requests");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM daily_settlement_summaries");

        insertOrder("IT-PAID-1", OrderStatus.PAID, day.atTime(9, 0), new BigDecimal("10000"));
        insertOrder("IT-DELIVERED-1", OrderStatus.DELIVERED, day.atTime(23, 30), new BigDecimal("20000"));
        insertOrder("IT-REFUNDED-1", OrderStatus.REFUNDED, day.atTime(12, 0), new BigDecimal("99999"));
        insertOrder("IT-OTHER-DAY", OrderStatus.PAID, day.plusDays(1).atTime(1, 0), new BigDecimal("5000"));

        Long paidOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_merchant_uid = ?", Long.class, "IT-PAID-1");
        jdbcTemplate.update(
                "INSERT INTO refunds (order_id, refund_amount, refunded_at) VALUES (?, ?, ?)",
                paidOrderId, new BigDecimal("1000"), day.atTime(18, 0));

        settlementBatchService.executeSettlementBatchForDate(day);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT daily_total_sales, daily_total_pg_fee, daily_total_platform_fee, "
                        + "daily_net_settlement, total_order_count "
                        + "FROM daily_settlement_summaries WHERE summary_date = ?",
                day);
        assertEquals(0, new BigDecimal("29000").compareTo(asDecimal(row.get("daily_total_sales"))));
        assertEquals(0, new BigDecimal("957.00").compareTo(asDecimal(row.get("daily_total_pg_fee"))));
        assertEquals(0, new BigDecimal("2900.00").compareTo(asDecimal(row.get("daily_total_platform_fee"))));
        assertEquals(0, new BigDecimal("25143.00").compareTo(asDecimal(row.get("daily_net_settlement"))));
        assertEquals(2L, ((Number) row.get("total_order_count")).longValue());

        insertOrder("IT-LEDGER-KEEP", OrderStatus.PAID, day.atTime(8, 0), new BigDecimal("4000"));
        insertOrder("IT-LEDGER-DROP", OrderStatus.REFUNDED, day.atTime(8, 0), new BigDecimal("8000"));
        settlementBatchService.rebuildDailySummariesFromOrderLedger();
        Long rebuiltCount = jdbcTemplate.queryForObject(
                "SELECT total_order_count FROM daily_settlement_summaries WHERE summary_date = ?",
                Long.class,
                day);
        Long includedOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE CAST(order_date AS date) = ? "
                        + "AND (status IS NULL OR status NOT IN ('CANCELLED','RETURNED','REFUNDED'))",
                Long.class,
                day);
        assertEquals(includedOrders, rebuiltCount);
        verifyNoPortOneHttp();
    }

    @Test
    void testProfileDoesNotCallPortOneHttpOnMockCompleteOrBlankSecretRefundApis() throws Exception {
        Fixture fixture = seedReceivedReturn(5, 1);
        returnRequestService.mockComplete(fixture.orderId);

        Payment payment = new Payment();
        payment.setPgPaymentId("pay_must_not_leave_test_jvm");
        portOneRefundService.cancelPayment(payment, "고객 즉시 주문취소");
        IllegalStateException missingSecret = assertThrows(
                IllegalStateException.class,
                () -> portOneRefundService.cancelPaymentForReturnRefund(payment, "관리자 반품 승인 환불"));
        assertTrue(missingSecret.getMessage().contains("시크릿"));
        verifyNoPortOneHttp();
    }

    private void runMockComplete(
            Long orderId,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts,
            AtomicReference<Throwable> unexpected) {
        try {
            start.await(5, TimeUnit.SECONDS);
            returnRequestService.mockComplete(orderId);
            successes.incrementAndGet();
        } catch (IllegalStateException ex) {
            conflicts.incrementAndGet();
        } catch (Exception ex) {
            unexpected.compareAndSet(null, ex);
        }
    }

    private Fixture seedReceivedReturn(int stock, int count) {
        User user = new User();
        user.setUserKey("it-" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(Role.USER);
        user = userRepository.save(user);

        Product product = new Product();
        product.setName("it-product");
        product.setPrice(new BigDecimal("10000"));
        product.setStockQuantity(stock);
        product = productRepository.save(product);

        Orders order = new Orders();
        order.setUser(user);
        order.setOrderMerchantUid("IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setTotalAmount(new BigDecimal("10000"));
        order.setNetAmount(new BigDecimal("10000"));
        order.setOrderDate(LocalDateTime.now());
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setCount(count);
        item.setOrderPrice(new BigDecimal("10000"));
        order.setOrderItems(List.of(item));
        order = ordersRepository.save(order);

        ReturnRequest request = new ReturnRequest();
        request.setOrder(order);
        request.setStatus(ReturnStatus.RECEIVED);
        request.setRequestedAt(LocalDateTime.now());
        request = returnRequestRepository.save(request);

        return new Fixture(order.getId(), product.getId(), request.getId());
    }

    private void insertOrder(String merchantUid, OrderStatus status, LocalDateTime orderDate, BigDecimal netAmount) {
        jdbcTemplate.update(
                "INSERT INTO orders (order_merchant_uid, status, net_amount, total_amount, order_date, "
                        + "delivery_fee, pg_fee, platform_fee, partner_settlement_amount) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0)",
                merchantUid, status.name(), netAmount, netAmount, orderDate);
    }

    private void verifyNoPortOneHttp() {
        try {
            verify(httpClientProbe, never()).send(any(HttpRequest.class), any());
        } catch (Exception e) {
            fail(e);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        StringBuilder text = new StringBuilder(String.valueOf(error.getMessage()));
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            text.append(' ').append(current.getMessage());
        }
        return text.toString();
    }

    private static BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private record Fixture(Long orderId, Long productId, Long returnRequestId) {
    }
}
