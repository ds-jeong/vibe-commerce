package com.example.ecommerce.controller;

import com.example.ecommerce.service.AnalyticsService;
import com.example.ecommerce.service.DataGeneratorService;
import com.example.ecommerce.service.SettlementBatchService;
import com.example.ecommerce.service.SettlementLedgerBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;
    private final DataGeneratorService dataGeneratorService;
    private final SettlementBatchService settlementBatchService;
    private final SettlementLedgerBackfillService settlementLedgerBackfillService;

    public AdminAnalyticsController(
            AnalyticsService analyticsService,
            DataGeneratorService dataGeneratorService,
            SettlementBatchService settlementBatchService,
            SettlementLedgerBackfillService settlementLedgerBackfillService
    ) {
        this.analyticsService = analyticsService;
        this.dataGeneratorService = dataGeneratorService;
        this.settlementBatchService = settlementBatchService;
        this.settlementLedgerBackfillService = settlementLedgerBackfillService;
    }

    @GetMapping("/sales-trend")
    public ResponseEntity<?> salesTrend(@RequestParam(required = false, defaultValue = "daily") String grain) {
        return ResponseEntity.ok(analyticsService.salesTrend(grain));
    }

    @GetMapping("/product-sales")
    public ResponseEntity<?> productSales(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false, defaultValue = "amount") String sort
    ) {
        return ResponseEntity.ok(analyticsService.productSales(page, size, sort));
    }

    @GetMapping("/inquiries")
    public ResponseEntity<?> inquiries(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "8") Integer size
    ) {
        return ResponseEntity.ok(analyticsService.inquiries(status, page, size));
    }

    @PostMapping("/sync-ledger-sample")
    public ResponseEntity<?> syncLedgerSample(@RequestParam(required = false, defaultValue = "1000") int count) {
        long elapsed = dataGeneratorService.generateLedgerSampleData(count);
        settlementLedgerBackfillService.backfillMissingLedgerFields();
        int days = settlementBatchService.rebuildDailySummariesFromOrderLedger();
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUCCESS");
        body.put("elapsedTimeMs", elapsed);
        body.put("summaryDays", days);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/backfill-ledger")
    public ResponseEntity<?> backfillLedger() {
        int updated = settlementLedgerBackfillService.backfillMissingLedgerFields();
        int days = settlementBatchService.rebuildDailySummariesFromOrderLedger();
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUCCESS");
        body.put("updatedOrders", updated);
        body.put("summaryDays", days);
        return ResponseEntity.ok(body);
    }
}
