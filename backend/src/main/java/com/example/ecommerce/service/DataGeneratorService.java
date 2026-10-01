package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private final SettlementLedgerService settlementLedgerService;
    private static final int CHUNK_SIZE = 1000;

    @Transactional
    public long generateBulkData(int totalRecords) {
        long startTime = System.currentTimeMillis();

        log.info("이커머스 합성 데이터 {}건 생성 및 벌크 인서트 파이프라인 가동...", totalRecords);

        // 1. 기초 마스터 데이터 세팅 (테스트용 상품 20개 생성 및 적재)
        List<Long> productIds = setupMockProducts();
        // 2. 기초 마스터 데이터 세팅 (테스트용 회원 100명 생성 및 적재)
        List<Long> userIds = setupMockUsers();

        // 3. 메인 트랜잭션 대량 생성을 위한 청크 분할 루프 구동
        int generatedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoYearsAgo = now.minusYears(2);

        while (generatedCount < totalRecords) {
            int currentBatchSize = Math.min(CHUNK_SIZE, totalRecords - generatedCount);
            
            List<Object[]> orderRows = new ArrayList<>();
            List<Object[]> itemRows = new ArrayList<>();
            List<Object[]> paymentRows = new ArrayList<>();
            List<Object[]> settlementRows = new ArrayList<>();
            List<Object[]> refundRows = new ArrayList<>();

            for (int i = 0; i < currentBatchSize; i++) {
                String merchantUid = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Long userId = userIds.get(ThreadLocalRandom.current().nextInt(userIds.size()));
                Long productId = productIds.get(ThreadLocalRandom.current().nextInt(productIds.size()));
                
                // 2년 범위 내 무작위 주문 날짜 분산
                long days = ChronoUnit.DAYS.between(twoYearsAgo, now);
                LocalDateTime orderDate = twoYearsAgo.plusDays(ThreadLocalRandom.current().nextLong(days + 1))
                                                     .plusHours(ThreadLocalRandom.current().nextInt(24))
                                                     .plusMinutes(ThreadLocalRandom.current().nextInt(60));

                // 비즈니스 상태 비중 분기 (80% DELIVERED / 10% PAID / 10% REFUNDED)
                int ratio = ThreadLocalRandom.current().nextInt(100);
                String status = "DELIVERED";
                if (ratio < 10) {
                    status = "PAID";
                } else if (ratio < 20) {
                    status = "REFUNDED";
                }

                // 금액 및 수수료 회계 정합성 연산 (BigDecimal 보호 모드)
                BigDecimal productPrice = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(3, 16) * 10000L); // 3만 원 ~ 15만 원 상품 단가 무작위 추출
                BigDecimal count = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 4));
                BigDecimal totalAmount = productPrice.multiply(count);
                BigDecimal discountAmount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(0, 3) * 2000L); // 0원 ~ 4천 원 쿠폰 할인액 무작위 적용
                BigDecimal netAmount = totalAmount.subtract(discountAmount);

                if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    netAmount = BigDecimal.valueOf(10000L); // 최소 방어 단가 적용
                }

                // 리스트 적재 (현재 Sequence 가상 생성으로 임시 ID 연산 대비)
                // 가상 데이터 세정을 위한 배치 파싱 구조 
                orderRows.add(new Object[]{userId, merchantUid, status, totalAmount, discountAmount, netAmount, orderDate});
            }

            // 가상 식별자 획득 및 연쇄 테이블 벌크 트랜잭션 묶음 갱신
            // 장부 보존을 위한 순차 배치 명령 실행
            executeChunkInserts(orderRows, statusRouter(orderRows));
            
            generatedCount += currentBatchSize;
            log.info("합성 데이터 청크 파이프라인 처리 중... ({}/{}) 완료", generatedCount, totalRecords);
        }

        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    private List<Long> setupMockProducts() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, refunds, settlements, payments, orders, products, users RESTART IDENTITY CASCADE");
        List<Long> ids = new ArrayList<>();
        String[] names = {"모던 핏 슬랙스", "오버사이즈 후드티", "스마트 무선 헤드폰", "가죽 미니멀 지갑", "세라믹 머그컵", "어반 캔버스 백팩", "프리미엄 러닝화"};
        for (int i = 1; i <= 20; i++) {
            String name = names[ThreadLocalRandom.current().nextInt(names.length)] + " " + i;
            BigDecimal price = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(3, 16) * 10000L);
            jdbcTemplate.update("INSERT INTO products (name, price, stock_quantity, image_url, description, created_at) VALUES (?, ?, 100, 'https://picsum.photos', '상세 설명', NOW())", name, price);
            ids.add((long) i);
        }
        return ids;
    }

    private List<Long> setupMockUsers() {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            jdbcTemplate.update("INSERT INTO users (user_key, password, name, phone_number, role, created_at) VALUES (?, 'password', ?, '010-1234-5678', 'USER', NOW())", "user" + i + "@vibe.com", "테스터" + i);
            ids.add((long) i);
        }
        return ids;
    }

    private List<String> statusRouter(List<Object[]> orders) {
        List<String> statuses = new ArrayList<>();
        for (Object[] o : orders) statuses.add((String) o[2]);
        return statuses;
    }

    private void executeChunkInserts(List<Object[]> orderRows, List<String> statuses) {
        // 1. Orders 벌크 인서트 수행
        String orderSql = "INSERT INTO orders (user_id, order_merchant_uid, status, total_amount, discount_amount, net_amount, order_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(orderSql, orderRows);

        // 2. 가상 정산/결제 전표 동기화를 위한 Last ID 역추적 매핑 기법 적용
        List<Long> lastOrderIds = jdbcTemplate.queryForList("SELECT id FROM orders ORDER BY id DESC LIMIT " + orderRows.size(), Long.class);
        
        List<Object[]> itemBatch = new ArrayList<>();
        List<Object[]> payBatch = new ArrayList<>();
        List<Object[]> settleBatch = new ArrayList<>();
        List<Object[]> refundBatch = new ArrayList<>();

        int idx = 0;
        // 최신 생성 역순 정렬 스캔
        for (int i = lastOrderIds.size() - 1; i >= 0; i--) {
            Long orderId = lastOrderIds.get(i);
            Object[] originOrder = orderRows.get(idx);
            String status = statuses.get(idx);
            
            BigDecimal netAmount = (BigDecimal) originOrder[5];
            LocalDateTime oDate = (LocalDateTime) originOrder[6];

            // 수자원연구소 수식 계승식: 수수료 회계 계산 (BigDecimal 소수점 반올림 통제)
            BigDecimal pgFee = netAmount.multiply(BigDecimal.valueOf(0.033)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal platformFee = netAmount.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netSettlement = netAmount.subtract(pgFee).subtract(platformFee).setScale(2, RoundingMode.HALF_UP);

            // 주문 상세 1대1 무작위 가상 적재
            itemBatch.add(new Object[]{orderId, 1L, netAmount, 1});

            // 결제 데이터 매핑 (PAID, DELIVERED, REFUNDED 공통)
            payBatch.add(new Object[]{orderId, "imp_" + UUID.randomUUID().toString().substring(0, 8), "PORTONE", "CARD", netAmount, oDate});

            // 정산 기초 데이터 장부 적재
            settleBatch.add(new Object[]{orderId, netAmount, pgFee, platformFee, netSettlement, oDate});

            // 환불 상태 처리 분기 시 마이너스 전표 동시 발행
            if ("REFUNDED".equals(status)) {
                refundBatch.add(new Object[]{orderId, "cancel_" + UUID.randomUUID().toString().substring(0, 8), netAmount, pgFee, platformFee, netSettlement, "소비자 변심 환불", oDate.plusDays(2)});
            }
            idx++;
        }

        // 배치 단위 동시 적재 트랜잭션 마감
        jdbcTemplate.batchUpdate("INSERT INTO order_items (order_id, product_id, order_price, count) VALUES (?, ?, ?, ?)", itemBatch);
        jdbcTemplate.batchUpdate("INSERT INTO payments (order_id, pg_imp_uid, pg_provider, pay_method, amount, paid_at) VALUES (?, ?, ?, ?, ?, ?)", payBatch);
        jdbcTemplate.batchUpdate("INSERT INTO settlements (order_id, total_sales_amount, pg_fee, platform_fee, net_settlement_amount, settled_at) VALUES (?, ?, ?, ?, ?, ?)", settleBatch);
        if (!refundBatch.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO refunds (order_id, pg_cancel_uid, refund_amount, refund_pg_fee, refund_platform_fee, net_refund_amount, refund_reason, refunded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", refundBatch);
        }
    }

    @Transactional
    public long generateLedgerSampleData(int totalRecords) {
        long startTime = System.currentTimeMillis();
        int target = Math.max(totalRecords, 1);
        List<Long> productIds = ensureSampleProducts();
        List<Long> userIds = ensureSampleUsers();
        Map<Long, BigDecimal> productPrices = new HashMap<>();
        jdbcTemplate.query("SELECT id, price FROM products", (rs) -> {
            productPrices.put(rs.getLong("id"), rs.getBigDecimal("price"));
        });

        int generatedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.minusMonths(18);

        while (generatedCount < target) {
            int currentBatchSize = Math.min(CHUNK_SIZE, target - generatedCount);
            List<Object[]> orderRows = new ArrayList<>();
            List<Long> productChoices = new ArrayList<>();
            List<Integer> quantities = new ArrayList<>();
            List<BigDecimal> unitPrices = new ArrayList<>();

            for (int i = 0; i < currentBatchSize; i++) {
                String merchantUid = "LEDGER-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                Long userId = userIds.get(ThreadLocalRandom.current().nextInt(userIds.size()));
                Long productId = productIds.get(ThreadLocalRandom.current().nextInt(productIds.size()));
                long days = ChronoUnit.DAYS.between(rangeStart, now);
                LocalDateTime orderDate = rangeStart.plusDays(ThreadLocalRandom.current().nextLong(days + 1))
                        .plusHours(ThreadLocalRandom.current().nextInt(24))
                        .plusMinutes(ThreadLocalRandom.current().nextInt(60));

                int ratio = ThreadLocalRandom.current().nextInt(100);
                String status = "DELIVERED";
                String tracking = null;
                if (ratio < 8) {
                    status = "PAID";
                } else if (ratio < 18) {
                    status = "SHIPPING";
                    tracking = String.valueOf(1000000000000L + ThreadLocalRandom.current().nextLong(8999999999999L));
                } else if (ratio < 23) {
                    status = "PREPARING";
                } else {
                    tracking = String.valueOf(1000000000000L + ThreadLocalRandom.current().nextLong(8999999999999L));
                }

                int count = ThreadLocalRandom.current().nextInt(1, 4);
                BigDecimal unitPrice = productPrices.get(productId);
                if (unitPrice == null) {
                    unitPrice = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(3, 16) * 10000L);
                }
                BigDecimal goods = unitPrice.multiply(BigDecimal.valueOf(count));
                var ledger = settlementLedgerService.computeLedger(goods);

                orderRows.add(new Object[]{
                        userId,
                        merchantUid,
                        status,
                        ledger.get("totalAmount"),
                        BigDecimal.ZERO,
                        ledger.get("totalAmount"),
                        orderDate,
                        ledger.get("deliveryFee"),
                        ledger.get("pgFee"),
                        ledger.get("platformFee"),
                        ledger.get("partnerSettlementAmount"),
                        tracking
                });
                productChoices.add(productId);
                quantities.add(count);
                unitPrices.add(unitPrice);
            }

            executeLedgerChunkInserts(orderRows, productChoices, quantities, unitPrices);
            generatedCount += currentBatchSize;
            log.info("정산 전표 샘플 청크 적재 ({}/{})", generatedCount, target);
        }

        return System.currentTimeMillis() - startTime;
    }

    private List<Long> ensureSampleProducts() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM products", Long.class);
        if (!ids.isEmpty()) {
            return ids;
        }
        String[] names = {"모던 핏 슬랙스", "오버사이즈 후드티", "스마트 무선 헤드폰", "가죽 미니멀 지갑", "세라믹 머그컵"};
        for (int i = 1; i <= 10; i++) {
            String name = names[ThreadLocalRandom.current().nextInt(names.length)] + " " + i;
            BigDecimal price = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(3, 16) * 10000L);
            jdbcTemplate.update(
                    "INSERT INTO products (name, price, stock_quantity, image_url, description, created_at) VALUES (?, ?, 100, ?, '상세 설명', NOW())",
                    name,
                    price,
                    "https://picsum.photos/seed/vibe" + i + "/80/80"
            );
        }
        return jdbcTemplate.queryForList("SELECT id FROM products", Long.class);
    }

    private List<Long> ensureSampleUsers() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM users", Long.class);
        if (!ids.isEmpty()) {
            return ids;
        }
        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                    "INSERT INTO users (user_key, password, name, phone_number, role, created_at) VALUES (?, 'password', ?, '010-1234-5678', 'USER', NOW())",
                    "ledger" + i + "@vibe.com",
                    "정산테스터" + i
            );
        }
        return jdbcTemplate.queryForList("SELECT id FROM users", Long.class);
    }

    private void executeLedgerChunkInserts(
            List<Object[]> orderRows,
            List<Long> productChoices,
            List<Integer> quantities,
            List<BigDecimal> unitPrices
    ) {
        String orderSql = "INSERT INTO orders (user_id, order_merchant_uid, status, total_amount, discount_amount, net_amount, order_date, delivery_fee, pg_fee, platform_fee, partner_settlement_amount, tracking_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(orderSql, orderRows);

        List<Long> lastOrderIds = jdbcTemplate.queryForList(
                "SELECT id FROM orders WHERE order_merchant_uid LIKE 'LEDGER-%' ORDER BY id DESC LIMIT " + orderRows.size(),
                Long.class
        );

        List<Object[]> itemBatch = new ArrayList<>();
        List<Object[]> payBatch = new ArrayList<>();
        List<Object[]> settleBatch = new ArrayList<>();

        int idx = 0;
        for (int i = lastOrderIds.size() - 1; i >= 0; i--) {
            Long orderId = lastOrderIds.get(i);
            Object[] originOrder = orderRows.get(idx);
            BigDecimal netAmount = (BigDecimal) originOrder[5];
            LocalDateTime oDate = (LocalDateTime) originOrder[6];
            BigDecimal pgFee = (BigDecimal) originOrder[8];
            BigDecimal platformFee = (BigDecimal) originOrder[9];
            BigDecimal partner = (BigDecimal) originOrder[10];
            Long productId = productChoices.get(idx);
            Integer count = quantities.get(idx);
            BigDecimal unitPrice = unitPrices.get(idx);

            itemBatch.add(new Object[]{orderId, productId, unitPrice, count});
            payBatch.add(new Object[]{orderId, "imp_" + UUID.randomUUID().toString().substring(0, 8), "PORTONE", "CARD", netAmount, oDate});
            settleBatch.add(new Object[]{orderId, netAmount, pgFee, platformFee, partner, oDate});
            idx++;
        }

        jdbcTemplate.batchUpdate("INSERT INTO order_items (order_id, product_id, order_price, count) VALUES (?, ?, ?, ?)", itemBatch);
        jdbcTemplate.batchUpdate("INSERT INTO payments (order_id, pg_imp_uid, pg_provider, pay_method, amount, paid_at) VALUES (?, ?, ?, ?, ?, ?)", payBatch);
        jdbcTemplate.batchUpdate("INSERT INTO settlements (order_id, total_sales_amount, pg_fee, platform_fee, net_settlement_amount, settled_at) VALUES (?, ?, ?, ?, ?, ?)", settleBatch);
    }
}
