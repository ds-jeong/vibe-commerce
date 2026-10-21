package com.example.ecommerce.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 공개 상품 목록의 판매량순·좋아요순 정렬.
 * 판매량은 상품에 저장하지 않고 주문 상품 수량을 집계한다.
 * 포함: PAID, PREPARING, SHIPPING, DELIVERING, DELIVERED, RETURN_REQUESTED, REFUND_REQUESTED.
 * 제외: ORDERED, 상태 없음, CANCELLED, RETURNED, REFUNDED.
 * 부분 취소 수량은 주문 상품에 없어서 주문 전체가 포함되거나 제외된다.
 * 한 주문의 여러 상품은 order_items.count 를 상품별로 더한다.
 */
@Service
public class ProductCatalogQuery {

    static final String PUBLIC_SALE_STATUSES =
            "'PAID','PREPARING','SHIPPING','DELIVERING','DELIVERED','RETURN_REQUESTED','REFUND_REQUESTED'";

    private static final String SALES_METRIC =
            "SELECT oi.product_id AS product_id, COALESCE(SUM(oi.count), 0) AS metric_count "
                    + "FROM order_items oi JOIN orders o ON o.id = oi.order_id "
                    + "WHERE o.status IN (" + PUBLIC_SALE_STATUSES + ") "
                    + "GROUP BY oi.product_id";

    private static final String LIKES_METRIC =
            "SELECT pl.product_id AS product_id, COUNT(*) AS metric_count "
                    + "FROM product_likes pl GROUP BY pl.product_id";

    private final JdbcTemplate jdbcTemplate;

    public ProductCatalogQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SortKey parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        String key = sort.trim().toLowerCase(Locale.ROOT);
        if ("sales".equals(key) || "quantity".equals(key) || "sold".equals(key)) {
            return SortKey.SALES;
        }
        if ("likes".equals(key) || "likecount".equals(key)) {
            return SortKey.LIKES;
        }
        return null;
    }

    public CatalogPage find(String keyword, Long categoryId, SortKey sortKey, Integer page, Integer size) {
        boolean paged = page != null && size != null && page >= 0 && size > 0;
        String metric = sortKey == SortKey.LIKES ? LIKES_METRIC : SALES_METRIC;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> filters = new ArrayList<>();
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
            filters.add(categoryId);
        }
        String trimmed = keyword == null ? "" : keyword.trim();
        if (!trimmed.isEmpty()) {
            where.append(" AND LOWER(p.name) LIKE LOWER(?)");
            filters.add("%" + trimmed + "%");
        }
        String from = " FROM products p LEFT JOIN (" + metric + ") metric ON metric.product_id = p.id" + where;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from,
                Long.class,
                filters.toArray());
        String ordered = "SELECT p.id" + from + " ORDER BY COALESCE(metric.metric_count, 0) DESC, p.id DESC";
        List<Long> ids;
        if (paged) {
            List<Object> args = new ArrayList<>(filters);
            args.add(size);
            args.add((long) page * size);
            ids = jdbcTemplate.query(ordered + " LIMIT ? OFFSET ?", (rs, rowNum) -> rs.getLong(1), args.toArray());
        } else {
            ids = jdbcTemplate.query(ordered, (rs, rowNum) -> rs.getLong(1), filters.toArray());
        }
        return new CatalogPage(ids, total == null ? 0 : total);
    }

    public enum SortKey {
        SALES,
        LIKES
    }

    public record CatalogPage(List<Long> ids, long total) {
    }
}
