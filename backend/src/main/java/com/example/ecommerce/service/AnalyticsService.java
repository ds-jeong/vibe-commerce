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
        String orderBy = "quantity".equals(sortKey)
                ? "quantity DESC, amount DESC"
                : "netRevenue".equals(sortKey) || "net".equals(sortKey)
                ? "netRevenue DESC, amount DESC"
                : "amount DESC, quantity DESC";

        String fromSql = " FROM order_items oi "
                + "JOIN products p ON oi.product_id = p.id "
                + "JOIN orders o ON oi.order_id = o.id "
                + "WHERE o.status IS NULL OR o.status NOT IN (" + EXCLUDED_STATUSES + ") ";

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT p.id " + fromSql + " GROUP BY p.id) grouped",
                Long.class
        );
        int totalCount = total == null ? 0 : total.intValue();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id as id, p.name as name, p.image_url as imageUrl, "
                        + "COALESCE(SUM(oi.count), 0) as quantity, "
                        + "COALESCE(SUM(oi.order_price * oi.count), 0) as amount, "
                        + "COALESCE(SUM(CASE "
                        + "WHEN (COALESCE(o.total_amount, o.net_amount, 0) - COALESCE(o.delivery_fee, 0)) <= 0 THEN 0 "
                        + "ELSE (oi.order_price * oi.count) "
                        + "/ (COALESCE(o.total_amount, o.net_amount) - COALESCE(o.delivery_fee, 0)) "
                        + "* COALESCE(o.partner_settlement_amount, "
                        + "COALESCE(o.total_amount, o.net_amount, 0) "
                        + "- ROUND(COALESCE(o.total_amount, o.net_amount, 0) * 0.033, 2) "
                        + "- ROUND((COALESCE(o.total_amount, o.net_amount, 0) - COALESCE(o.delivery_fee, 0)) * 0.10, 2)) "
                        + "END), 0) as netRevenue "
                        + fromSql
                        + "GROUP BY p.id, p.name, p.image_url "
                        + "ORDER BY " + orderBy
                        + " LIMIT ? OFFSET ?",
                safeSize,
                safePage * safeSize
        );

        Map<String, Object> body = new HashMap<>();
        body.put("content", rows);
        body.put("totalElements", totalCount);
        body.put("totalPages", totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) safeSize));
        body.put("number", safePage);
        body.put("size", safeSize);
        body.put("sort", "quantity".equals(sortKey) ? "quantity" : ("netRevenue".equals(sortKey) || "net".equals(sortKey) ? "netRevenue" : "amount"));
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
