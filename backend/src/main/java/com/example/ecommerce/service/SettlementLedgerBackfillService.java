package com.example.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementLedgerBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SettlementLedgerBackfillService.class);

    private final JdbcTemplate jdbcTemplate;

    public SettlementLedgerBackfillService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void ensureLedgerColumns() {
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_fee numeric(19,2)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS pg_fee numeric(19,2)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee numeric(19,2)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS partner_settlement_amount numeric(19,2)");
    }

    @Transactional
    public int backfillMissingLedgerFields() {
        ensureLedgerColumns();
        int updated = jdbcTemplate.update(
                "UPDATE orders SET "
                        + "delivery_fee = COALESCE(delivery_fee, 0), "
                        + "pg_fee = COALESCE(pg_fee, ROUND(COALESCE(net_amount, 0) * 0.033, 2)), "
                        + "platform_fee = COALESCE(platform_fee, ROUND(COALESCE(net_amount, 0) * 0.10, 2)), "
                        + "partner_settlement_amount = COALESCE(partner_settlement_amount, "
                        + "COALESCE(net_amount, 0) - ROUND(COALESCE(net_amount, 0) * 0.033, 2) "
                        + "- ROUND(COALESCE(net_amount, 0) * 0.10, 2)) "
                        + "WHERE pg_fee IS NULL OR platform_fee IS NULL OR partner_settlement_amount IS NULL OR delivery_fee IS NULL"
        );
        jdbcTemplate.update(
                "INSERT INTO settlements (order_id, total_sales_amount, pg_fee, platform_fee, net_settlement_amount, settled_at) "
                        + "SELECT o.id, COALESCE(o.total_amount, o.net_amount), o.pg_fee, o.platform_fee, o.partner_settlement_amount, COALESCE(o.order_date, NOW()) "
                        + "FROM orders o "
                        + "WHERE NOT EXISTS (SELECT 1 FROM settlements s WHERE s.order_id = o.id) "
                        + "AND o.status IS NOT NULL AND o.status NOT IN ('CANCELLED')"
        );
        log.info("Backfilled settlement ledger fields on {} orders", updated);
        return updated;
    }
}
