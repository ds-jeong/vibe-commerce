package com.example.ecommerce.global;

import com.example.ecommerce.service.DataGeneratorService;
import com.example.ecommerce.service.SettlementBatchService;
import com.example.ecommerce.service.SettlementLedgerBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SettlementLedgerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SettlementLedgerBootstrap.class);

    private final SettlementLedgerBackfillService settlementLedgerBackfillService;
    private final DataGeneratorService dataGeneratorService;
    private final SettlementBatchService settlementBatchService;
    private final JdbcTemplate jdbcTemplate;

    public SettlementLedgerBootstrap(
            SettlementLedgerBackfillService settlementLedgerBackfillService,
            DataGeneratorService dataGeneratorService,
            SettlementBatchService settlementBatchService,
            JdbcTemplate jdbcTemplate
    ) {
        this.settlementLedgerBackfillService = settlementLedgerBackfillService;
        this.dataGeneratorService = dataGeneratorService;
        this.settlementBatchService = settlementBatchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            settlementLedgerBackfillService.ensureLedgerColumns();
            settlementLedgerBackfillService.backfillMissingLedgerFields();
            Long ledgerCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE order_merchant_uid LIKE 'LEDGER-%'",
                    Long.class
            );
            Long totalOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
            if ((ledgerCount == null || ledgerCount == 0L) && (totalOrders == null || totalOrders < 1000L)) {
                log.info("Seeding 1000 virtual settlement orders for analytics.");
                dataGeneratorService.generateLedgerSampleData(1000);
                settlementLedgerBackfillService.backfillMissingLedgerFields();
            }
            settlementBatchService.rebuildDailySummariesFromOrderLedger();
        } catch (Exception e) {
            log.warn("Settlement ledger bootstrap skipped: {}", e.getMessage());
        }
    }
}
