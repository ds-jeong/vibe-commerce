package com.example.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private InquiryService inquiryService;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(jdbcTemplate, inquiryService);
    }

    @Test
    void likeSortIncludesProductsWithoutSales() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);
        doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Integer.class), any(Integer.class));

        Map<String, Object> body = service.productSales(1, 8, "likes");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(8), eq(8));
        assertTrue(sql.getValue().contains("FROM products p"));
        assertTrue(sql.getValue().contains("product_likes"));
        assertTrue(sql.getValue().contains("ORDER BY \"likeCount\" DESC"));
        assertEquals("likes", body.get("sort"));
        assertEquals(2, body.get("totalPages"));
    }

    @Test
    void amountSortKeepsSoldProductsAndAddsLikeCount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Integer.class), any(Integer.class));

        Map<String, Object> body = service.productSales(0, 10, "amount");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class));
        verify(jdbcTemplate).queryForList(sql.capture(), eq(10), eq(0));
        assertTrue(countSql.getValue().contains("GROUP BY p.id"));
        assertTrue(sql.getValue().contains("FROM order_items oi"));
        assertTrue(sql.getValue().contains("product_likes"));
        assertTrue(sql.getValue().contains("ORDER BY amount DESC"));
        assertFalse(sql.getValue().contains("LEFT JOIN"));
        assertEquals("amount", body.get("sort"));
    }
}
