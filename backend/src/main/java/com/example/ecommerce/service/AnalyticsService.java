package com.example.ecommerce.service;

import com.example.ecommerce.global.PagingSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final String EXCLUDED_STATUSES = "'CANCELLED','RETURNED','REFUNDED'";

    private final JdbcTemplate jdbcTemplate;
    private final InquiryService inquiryService;

    public AnalyticsService(JdbcTemplate jdbcTemplate, InquiryService inquiryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inquiryService = inquiryService;
    }

    public List<Map<String, Object>> salesTrend(String grain) {
        boolean monthly = grain != null && "monthly".equalsIgnoreCase(grain.trim());
        if (monthly) {
            return jdbcTemplate.queryForList(
                    "SELECT TO_CHAR(DATE_TRUNC('month', order_date), 'YYYY-MM') as date, "
                            + "COALESCE(SUM(COALESCE(total_amount, net_amount)), 0) as sales, "
                            + "COALESCE(SUM(COALESCE(pg_fee, 0)), 0) as pgFee, "
                            + "COALESCE(SUM(COALESCE(platform_fee, 0)), 0) as platformFee, "
                            + "COALESCE(SUM(COALESCE(partner_settlement_amount, 0)), 0) as settlement, "
                            + "COUNT(*) as orderCount "
                            + "FROM orders "
                            + "WHERE order_date IS NOT NULL AND (status IS NULL OR status NOT IN (" + EXCLUDED_STATUSES + ")) "
                            + "GROUP BY DATE_TRUNC('month', order_date) "
                            + "ORDER BY DATE_TRUNC('month', order_date) ASC"
            );
        }
        return jdbcTemplate.queryForList(
                "SELECT TO_CHAR(CAST(order_date AS date), 'YYYY-MM-DD') as date, "
                        + "COALESCE(SUM(COALESCE(total_amount, net_amount)), 0) as sales, "
                        + "COALESCE(SUM(COALESCE(pg_fee, 0)), 0) as pgFee, "
                        + "COALESCE(SUM(COALESCE(platform_fee, 0)), 0) as platformFee, "
                        + "COALESCE(SUM(COALESCE(partner_settlement_amount, 0)), 0) as settlement, "
                        + "COUNT(*) as orderCount "
                        + "FROM orders "
                        + "WHERE order_date IS NOT NULL AND (status IS NULL OR status NOT IN (" + EXCLUDED_STATUSES + ")) "
                        + "GROUP BY CAST(order_date AS date) "
                        + "ORDER BY CAST(order_date AS date) ASC"
        );
    }

    public Map<String, Object> productSales(Integer page, Integer size, String sort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, 100);
        String sortKey = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        boolean byLikes = "likes".equals(sortKey) || "likecount".equals(sortKey);
        boolean byQuantity = "quantity".equals(sortKey);
        boolean byNet = "netrevenue".equals(sortKey) || "net".equals(sortKey);
        String normalized = byLikes ? "likes" : byQuantity ? "quantity" : byNet ? "netRevenue" : "amount";
        String likeCountSql = "COALESCE((SELECT COUNT(*) FROM product_likes pl WHERE pl.product_id = p.id), 0)";

        if (byLikes) {
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT p.id as id, p.name as name, p.image_url as \"imageUrl\", "
                            + "COALESCE(sales.quantity, 0) as quantity, "
                            + "COALESCE(sales.amount, 0) as amount, "
                            + likeCountSql + " as \"likeCount\" "
                            + "FROM products p "
                            + "LEFT JOIN ("
                            + "SELECT oi.product_id, COALESCE(SUM(oi.count), 0) as quantity, "
                            + "COALESCE(SUM(oi.order_price * oi.count), 0) as amount "
                            + "FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                            + "WHERE o.status IS NULL OR o.status NOT IN (" + EXCLUDED_STATUSES + ") "
                            + "GROUP BY oi.product_id"
                            + ") sales ON sales.product_id = p.id "
                            + "ORDER BY \"likeCount\" DESC, amount DESC, p.id DESC "
                            + "LIMIT ? OFFSET ?",
                    safeSize,
                    safePage * safeSize
            );
            return productSalesPage(rows, total, safePage, safeSize, normalized);
        }

        String orderBy = byQuantity
                ? "quantity DESC, amount DESC"
                : byNet
                ? "\"netRevenue\" DESC, amount DESC"
                : "amount DESC, quantity DESC";

        String fromSql = " FROM order_items oi "
                + "JOIN products p ON oi.product_id = p.id "
                + "JOIN orders o ON oi.order_id = o.id "
                + "WHERE o.status IS NULL OR o.status NOT IN (" + EXCLUDED_STATUSES + ") ";

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT p.id " + fromSql + " GROUP BY p.id) grouped",
                Long.class
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id as id, p.name as name, p.image_url as \"imageUrl\", "
                        + "COALESCE(SUM(oi.count), 0) as quantity, "
                        + "COALESCE(SUM(oi.order_price * oi.count), 0) as amount, "
                        + likeCountSql + " as \"likeCount\", "
                        + "COALESCE(SUM(CASE "
                        + "WHEN (COALESCE(o.total_amount, o.net_amount, 0) - COALESCE(o.delivery_fee, 0)) <= 0 THEN 0 "
                        + "ELSE (oi.order_price * oi.count) "
                        + "/ (COALESCE(o.total_amount, o.net_amount) - COALESCE(o.delivery_fee, 0)) "
                        + "* COALESCE(o.partner_settlement_amount, "
                        + "COALESCE(o.total_amount, o.net_amount, 0) "
                        + "- ROUND(COALESCE(o.total_amount, o.net_amount, 0) * 0.033, 2) "
                        + "- ROUND((COALESCE(o.total_amount, o.net_amount, 0) - COALESCE(o.delivery_fee, 0)) * 0.10, 2)) "
                        + "END), 0) as \"netRevenue\" "
                        + fromSql
                        + "GROUP BY p.id, p.name, p.image_url "
                        + "ORDER BY " + orderBy
                        + " LIMIT ? OFFSET ?",
                safeSize,
                safePage * safeSize
        );

        return productSalesPage(rows, total, safePage, safeSize, normalized);
    }

    private Map<String, Object> productSalesPage(
            List<Map<String, Object>> rows,
            Long total,
            int page,
            int size,
            String sort) {
        int totalCount = total == null ? 0 : total.intValue();
        Map<String, Object> body = new HashMap<>();
        body.put("content", rows);
        body.put("totalElements", totalCount);
        body.put("totalPages", totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) size));
        body.put("number", page);
        body.put("size", size);
        body.put("sort", sort);
        return body;
    }

    public Map<String, Object> inquiries(String status, Integer page, Integer size) {
        List<Map<String, Object>> all = inquiryService.getAllInquiriesForAdmin();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String wanted = status.trim().toUpperCase(Locale.ROOT);
            all = all.stream()
                    .filter(row -> wanted.equals(String.valueOf(row.get("status"))))
                    .toList();
        }
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 8 : size;
        return PagingSupport.slice(all, safePage, safeSize);
    }
}
