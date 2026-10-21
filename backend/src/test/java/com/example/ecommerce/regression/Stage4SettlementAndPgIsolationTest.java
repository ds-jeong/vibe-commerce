package com.example.ecommerce.regression;

import com.example.ecommerce.controller.AdminOrderController;
import com.example.ecommerce.controller.AdminReturnController;
import com.example.ecommerce.controller.OrderController;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.global.SchemaCheckConstraintMigrator;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PortOneRefundService;
import com.example.ecommerce.service.ReturnRequestService;
import com.example.ecommerce.service.SettlementBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class Stage4SettlementAndPgIsolationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OrderService orderService;
    @Mock
    private ReturnRequestService returnRequestService;

    @Test
    void dailySettlementFormulaSubtractsRefundsTableOnly() {
        LocalDate day = LocalDate.of(2026, 10, 15);
        Map<String, Object> sales = new HashMap<>();
        sales.put("total_sales", new BigDecimal("100000"));
        sales.put("order_count", 2L);
        when(jdbcTemplate.queryForMap(anyString(), eq(day))).thenReturn(sales);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(day)))
                .thenReturn(new BigDecimal("10000"));

        new SettlementBatchService(jdbcTemplate).executeSettlementBatchForDate(day);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), eq(day));
        assertTrue(sql.getValue().contains("FROM orders"));
        assertTrue(sql.getValue().contains("'PAID'"));
        assertTrue(sql.getValue().contains("'DELIVERED'"));
        assertTrue(sql.getValue().contains("status IN ('PAID', 'DELIVERING', 'DELIVERED')"));
        assertFalse(sql.getValue().contains("'REFUNDED'"));

        ArgumentCaptor<String> refundSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(refundSql.capture(), eq(BigDecimal.class), eq(day));
        assertTrue(refundSql.getValue().contains("FROM refunds"));
        assertTrue(refundSql.getValue().contains("refund_amount"));

        verify(jdbcTemplate).update(eq("DELETE FROM daily_settlement_summaries WHERE summary_date = ?"), eq(day));

        verify(jdbcTemplate).update(
                anyString(),
                eq(day),
                eq(new BigDecimal("90000")),
                eq(new BigDecimal("2970.00")),
                eq(new BigDecimal("9000.00")),
                eq(new BigDecimal("78030.00")),
                eq(2L)
        );
    }

    @Test
    void rebuildSummariesExcludesCancelledReturnedRefunded() {
        when(jdbcTemplate.update(anyString())).thenReturn(1, 3);
        int upserted = new SettlementBatchService(jdbcTemplate).rebuildDailySummariesFromOrderLedger();
        assertEquals(3, upserted);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture());
        String insert = sql.getAllValues().get(1);
        assertTrue(insert.contains("CANCELLED"));
        assertTrue(insert.contains("RETURNED"));
        assertTrue(insert.contains("REFUNDED"));
        assertTrue(insert.contains("NOT IN"));
    }

    @Test
    void blankPortOneSecretNeverHitsHttpOnCancelButReturnRefundDoesNotFallbackToMock() {
        PortOneRefundService service = new PortOneRefundService();
        ReflectionTestUtils.setField(service, "apiSecret", "");
        Payment payment = new Payment();
        payment.setPgPaymentId("pay_live_should_not_be_called");
        service.cancelPayment(payment, "고객 즉시 주문취소");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.cancelPaymentForReturnRefund(payment, "관리자 반품 승인 환불"));
        assertTrue(ex.getMessage().contains("시크릿"));
    }

    @Test
    void schemaMigratorIncludesCompletedReturnStatus() {
        assertTrue(Arrays.stream(ReturnStatus.values()).anyMatch(status -> status == ReturnStatus.COMPLETED));
        SchemaCheckConstraintMigrator migrator = new SchemaCheckConstraintMigrator(jdbcTemplate);
        migrator.run(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(6)).execute(sql.capture());
        String returnCheck = sql.getAllValues().stream()
                .filter(value -> value.contains("return_requests_status_check") && value.contains("ADD CONSTRAINT"))
                .findFirst()
                .orElse("");
        assertTrue(returnCheck.contains("'COMPLETED'"));
        assertTrue(returnCheck.contains("'REQUESTED'"));
    }

    @Test
    void existingHttpContractsStayOnExpectedPaths() throws Exception {
        Method place = OrderController.class.getMethod("createOrder", String.class, Map.class);
        assertEquals("/api/orders/place", place.getAnnotation(PostMapping.class).value()[0]);
        Method verify = OrderController.class.getMethod("verifyPayment", Map.class);
        assertEquals("/api/orders/verify", verify.getAnnotation(PostMapping.class).value()[0]);
        Method cancel = OrderController.class.getMethod("cancelMyOrder", String.class, Long.class);
        assertEquals("/api/orders/{id}/cancel", cancel.getAnnotation(PostMapping.class).value()[0]);
        Method memberReturn = OrderController.class.getMethod("requestReturn", String.class, Long.class);
        assertEquals("/api/orders/{id}/return-request", memberReturn.getAnnotation(PostMapping.class).value()[0]);

        Method pgApprove = AdminOrderController.class.getMethod("approveReturn", Long.class);
        assertEquals("/{id}/return-approve", pgApprove.getAnnotation(PutMapping.class).value()[0]);
        assertEquals("/api/admin/orders", AdminOrderController.class.getAnnotation(RequestMapping.class).value()[0]);

        Method mockComplete = AdminReturnController.class.getMethod("mockComplete", Long.class);
        assertEquals("/orders/{id}/returns/mock-complete", mockComplete.getAnnotation(PutMapping.class).value()[0]);
        assertEquals("/api/admin", AdminReturnController.class.getAnnotation(RequestMapping.class).value()[0]);

        when(returnRequestService.mockComplete(10058L)).thenReturn(Map.of(
                "id", 10058L,
                "returnStatus", "COMPLETED",
                "status", "REFUNDED"));
        MockMvc returnsMvc = MockMvcBuilders.standaloneSetup(new AdminReturnController(returnRequestService)).build();
        returnsMvc.perform(put("/api/admin/orders/10058/returns/mock-complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnStatus").value("COMPLETED"));
        verify(orderService, never()).processReturnRefundWithSafety(any());
        verify(returnRequestService).mockComplete(10058L);

        when(orderService.processReturnRefundWithSafety(7L)).thenReturn(Map.of("status", "RETURNED"));
        MockMvc ordersMvc = MockMvcBuilders.standaloneSetup(new AdminOrderController(orderService)).build();
        ordersMvc.perform(put("/api/admin/orders/7/return-approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        verify(returnRequestService, never()).mockComplete(7L);

        when(returnRequestService.reject(eq(3L), any())).thenThrow(new IllegalStateException("이미 처리"));
        MockHttpServletResponse conflict = returnsMvc.perform(
                        put("/api/admin/orders/3/returns/reject")
                                .contentType("application/json")
                                .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse();
        assertTrue(conflict.getContentAsString().contains("FAIL"));
    }
}
