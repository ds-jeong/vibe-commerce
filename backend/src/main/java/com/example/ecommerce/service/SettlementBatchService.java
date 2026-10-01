package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * ✨ 실무형 크론 표현식 (Cron Expression)
     * 초 분 시 일 월 요일 ➡️ "0 0 0 * * *" = 매일 자정(00:00:00)에 자동으로 배치 가동
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void runDailySettlementBatch() {
        // 어제 날짜를 타겟으로 지정하여 스냅샷 집계
        LocalDate targetDate = LocalDate.now().minusDays(1);
        executeSettlementBatchForDate(targetDate);
    }

    /**
     * 수동 트리거 및 특정 날짜 수동 보정(Re-run)이 가능하도록 비즈니스 메서드 격리 설계
     */
    @Transactional
    public void executeSettlementBatchForDate(LocalDate targetDate) {
        log.info("=== [정산 배치 엔진] {} 일자 통계 요약 스냅샷 연산 가동 ===", targetDate);

        // 1. 해당 일자의 결제 완료 및 배송 완료된 총 매출액 및 건수 집계 SQL
        String salesSql = "SELECT COALESCE(SUM(net_amount), 0) as total_sales, COUNT(*) as order_count " +
                          "FROM orders WHERE DATE(order_date) = ? AND status IN ('PAID', 'DELIVERING', 'DELIVERED')";
        
        Map<String, Object> salesResult = jdbcTemplate.queryForMap(salesSql, targetDate);
        BigDecimal totalSales = (BigDecimal) salesResult.get("total_sales");
        Long orderCount = (Long) salesResult.get("order_count");

        // 2. 해당 일자의 환불(마이너스 전표) 처리된 총 차감액 집계 SQL
        String refundSql = "SELECT COALESCE(SUM(refund_amount), 0) as total_refund " +
                           "FROM refunds WHERE DATE(refunded_at) = ?";
        
        BigDecimal totalRefund = jdbcTemplate.queryForObject(refundSql, BigDecimal.class, targetDate);

        // 3. 순 매출 연산 (총매출 - 총환불액)
        BigDecimal netSales = totalSales.subtract(totalRefund);

        // 4. 수자원연구소 수식 현대화: 수수료 일괄 배치 정밀 연산 (소수점 반올림 제어)
        BigDecimal pgFee = netSales.multiply(BigDecimal.valueOf(0.033)).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal platformFee = netSales.multiply(BigDecimal.valueOf(0.10)).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal netSettlement = netSales.subtract(pgFee).subtract(platformFee).setScale(2, BigDecimal.ROUND_HALF_UP);

        // 5. 기존에 이미 집계된 동일 날짜 장부가 있다면 데이터 오염 방지를 위해 선제 삭제 (Idempotency 보장)
        jdbcTemplate.update("DELETE FROM daily_settlement_summaries WHERE summary_date = ?", targetDate);

        // 6. 통계 스냅샷 테이블에 최종 벌크 적재 수행
        String insertSql = "INSERT INTO daily_settlement_summaries " +
                           "(summary_date, daily_total_sales, daily_total_pg_fee, daily_total_platform_fee, daily_net_settlement, total_order_count) " +
                           "VALUES (?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(insertSql, targetDate, netSales, pgFee, platformFee, netSettlement, orderCount);

        log.info("=== [정산 배치 완료] {} 장부 이관 성공 (총순매출: {}, 순수익: {}) ===", targetDate, netSales, netSettlement);
    }

    /**
     * 주문 원장의 수수료 컬럼을 SUM/GROUP BY 하여 일별 요약 스냅샷을 재작성한다.
     * 기존 executeSettlementBatchForDate 집계 공식은 변경하지 않는다.
     */
    @Transactional
    public int rebuildDailySummariesFromOrderLedger() {
        jdbcTemplate.update(
                "DELETE FROM daily_settlement_summaries ds "
                        + "WHERE NOT EXISTS ("
                        + "SELECT 1 FROM orders o WHERE CAST(o.order_date AS date) = ds.summary_date)"
        );
        int upserted = jdbcTemplate.update(
                "INSERT INTO daily_settlement_summaries "
                        + "(summary_date, daily_total_sales, daily_total_pg_fee, daily_total_platform_fee, daily_net_settlement, total_order_count) "
                        + "SELECT CAST(order_date AS date), "
                        + "COALESCE(SUM(COALESCE(total_amount, net_amount)), 0), "
                        + "COALESCE(SUM(COALESCE(pg_fee, 0)), 0), "
                        + "COALESCE(SUM(COALESCE(platform_fee, 0)), 0), "
                        + "COALESCE(SUM(COALESCE(partner_settlement_amount, 0)), 0), "
                        + "COUNT(*) "
                        + "FROM orders "
                        + "WHERE order_date IS NOT NULL "
                        + "AND (status IS NULL OR status NOT IN ('CANCELLED','RETURNED','REFUNDED')) "
                        + "GROUP BY CAST(order_date AS date) "
                        + "ON CONFLICT (summary_date) DO UPDATE SET "
                        + "daily_total_sales = EXCLUDED.daily_total_sales, "
                        + "daily_total_pg_fee = EXCLUDED.daily_total_pg_fee, "
                        + "daily_total_platform_fee = EXCLUDED.daily_total_platform_fee, "
                        + "daily_net_settlement = EXCLUDED.daily_net_settlement, "
                        + "total_order_count = EXCLUDED.total_order_count"
        );
        log.info("Rebuilt daily settlement summaries from order ledger ({} date buckets)", upserted);
        return upserted;
    }
}
