package com.example.ecommerce.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogQueryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void salesSortSumsItemQuantityAndKeepsOnlySettledOrders() {
        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(2L);
        when(jdbcTemplate.query(pageSql.capture(), anyRowMapper(), any(Object[].class))).thenReturn(List.of(8L, 2L));

        ProductCatalogQuery.CatalogPage page = query().find("머그", 4L, ProductCatalogQuery.SortKey.SALES, 0, 12);

        String sql = pageSql.getValue();
        assertTrue(sql.contains("SUM(oi.count)"));
        assertTrue(sql.contains("GROUP BY oi.product_id"));
        assertTrue(sql.contains("COALESCE(metric.metric_count, 0) DESC"));
        for (String status : List.of(
                "PAID", "PREPARING", "SHIPPING", "DELIVERING", "DELIVERED", "RETURN_REQUESTED", "REFUND_REQUESTED")) {
            assertTrue(sql.contains("'" + status + "'"), status);
        }
        for (String status : List.of("ORDERED", "CANCELLED", "RETURNED", "REFUNDED")) {
            assertFalse(sql.contains("'" + status + "'"), status);
        }
        assertEquals(List.of(8L, 2L), page.ids());
        assertEquals(2L, page.total());
        verify(jdbcTemplate).queryForObject(any(String.class), eq(Long.class), any(Object[].class));
    }

    @Test
    void likesSortUsesLikeRowsInsteadOfOrders() {
        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(pageSql.capture(), anyRowMapper(), any(Object[].class))).thenReturn(List.of());

        query().find(null, null, ProductCatalogQuery.SortKey.LIKES, 1, 12);

        String sql = pageSql.getValue();
        assertTrue(sql.contains("FROM product_likes pl"));
        assertFalse(sql.contains("order_items"));
        assertTrue(sql.contains("OFFSET ?"));
    }

    @Test
    void unknownSortStaysOnTheDefaultNewestOrder() {
        ProductCatalogQuery query = query();
        assertNull(query.parseSort(null));
        assertNull(query.parseSort("newest"));
        assertEquals(ProductCatalogQuery.SortKey.SALES, query.parseSort("sales"));
        assertEquals(ProductCatalogQuery.SortKey.LIKES, query.parseSort("likes"));
    }

    private ProductCatalogQuery query() {
        return new ProductCatalogQuery(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private RowMapper<Long> anyRowMapper() {
        return any(RowMapper.class);
    }
}
