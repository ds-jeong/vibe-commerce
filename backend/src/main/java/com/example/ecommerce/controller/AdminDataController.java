package com.example.ecommerce.controller;

import com.example.ecommerce.service.DataGeneratorService;
import com.example.ecommerce.service.SettlementBatchService;
import com.example.ecommerce.service.ExcelService;
import com.example.ecommerce.global.JwtProvider; // ✨ 추가
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDataController {

    private final DataGeneratorService dataGeneratorService;
    private final SettlementBatchService settlementBatchService;
    private final ExcelService excelService;
    private final JwtProvider jwtProvider; // ✨ 1. 토큰 제공 엔진 필드 추가
    private final JdbcTemplate jdbcTemplate;

    // ✨ 2. 무결성 수동 생성자 바인딩 레이어에 JwtProvider 조각 최종 링킹 완료!
    public AdminDataController(DataGeneratorService dataGeneratorService, 
                                SettlementBatchService settlementBatchService, 
                                ExcelService excelService,
                                JwtProvider jwtProvider, // 주입 인자 바인딩
                                JdbcTemplate jdbcTemplate) {
        this.dataGeneratorService = dataGeneratorService;
        this.settlementBatchService = settlementBatchService;
        this.excelService = excelService;
        this.jwtProvider = jwtProvider; // 매핑 완수
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 🔓 [신설 통합] 최고관리자 전용 보안 로그인 토큰 발급 API 채널
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> adminLogin(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        // 실무형 하드코딩 테스트 인증 바인딩 (아이디: admin / 패스워드: admin1234)
        if ("admin".equals(username) && "admin1234".equals(password)) {
            // 자격 증명 성공 시 권한을 'ADMIN' 사양으로 동적 낙인 찍어 JWT 토큰 생성
            String token = jwtProvider.createToken(username, "ADMIN");

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("accessToken", token); // 프론트엔드가 보관할 암호화 전표 송출
            response.put("role", "ADMIN");
            
            return ResponseEntity.ok(response);
        }

        // 인증 실패 시 410 혹은 401 Unauthorized 웹 예외 블로킹 리포트 반환
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "FAIL");
        errorResponse.put("message", "최고관리자 자격 증명 정보가 불일치합니다.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * 1. 10,000건 대량 Mock 데이터 적재 API
     */
    @PostMapping("/setup-mock-data")
    public ResponseEntity<Map<String, Object>> setupMockData(
            @RequestParam(defaultValue = "10000") int totalRecords) {
        long elapsedMs = dataGeneratorService.generateBulkData(totalRecords);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", totalRecords + "건의 커머스 합성 데이터 파이프라인 이관 완수");
        response.put("elapsedTimeMs", elapsedMs);
        response.put("architecture", "JdbcTemplate Batch-Chunk Update Method");
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 정산 통계 배치 수동 강제 가동 API (30일 분량)
     */
    @PostMapping("/run-batch")
    public ResponseEntity<Map<String, Object>> triggerSettlementBatch(
            @RequestParam(defaultValue = "30") int daysAgo) {
        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(daysAgo);
        int processedDays = 0;
        for (LocalDate date = startDate; date.isBefore(today); date = date.plusDays(1)) {
            settlementBatchService.executeSettlementBatchForDate(date);
            processedDays++;
        }
        long endTime = System.currentTimeMillis();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", processedDays + "일 치 정산서 데이터 요약 스냅샷 이관 완수");
        response.put("elapsedTimeMs", (endTime - startTime));
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 프론트엔드 대시보드 차트 연동용 API (30건 슬라이싱)
     */
    @GetMapping("/dashboard-stats")
    public ResponseEntity<List<Map<String, Object>>> getDashboardStats() {
        String sql = "SELECT summary_date as date, daily_total_sales as sales, " +
                     "daily_total_pg_fee as pgFee, daily_total_platform_fee as platformFee, " +
                     "daily_net_settlement as settlement, total_order_count as orderCount " +
                     "FROM daily_settlement_summaries " +
                     "ORDER BY summary_date ASC LIMIT 30";
        List<Map<String, Object>> stats = this.jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(stats);
    }

    /**
     * 4. 최고관리자 증빙용 정산서 내역 실물 엑셀 다운로드 API
     */
    @GetMapping("/download-excel")
    public ResponseEntity<InputStreamResource> downloadSettlementExcel() throws IOException {
        ByteArrayInputStream in = excelService.exportSettlementExcel();
        String fileName = "settlement_report_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + fileName);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getLiveDashboardStats() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("daily", jdbcTemplate.queryForList(
                    "SELECT summary_date as date, daily_total_sales as sales, " +
                            "daily_total_pg_fee as pgFee, daily_total_platform_fee as platformFee, " +
                            "daily_net_settlement as settlement, total_order_count as orderCount " +
                            "FROM daily_settlement_summaries ORDER BY summary_date ASC LIMIT 30"));
        } catch (Exception e) {
            result.put("daily", List.of());
        }
        try {
            result.put("statusCounts", jdbcTemplate.queryForList(
                    "SELECT COALESCE(status, 'ORDERED') as name, COUNT(*) as value FROM orders GROUP BY status"));
        } catch (Exception e) {
            result.put("statusCounts", List.of());
        }
        try {
            result.put("productSales", jdbcTemplate.queryForList(
                    "SELECT p.name as name, COALESCE(SUM(oi.count),0) as quantity, " +
                            "COALESCE(SUM(oi.order_price * oi.count),0) as amount " +
                            "FROM order_items oi JOIN products p ON oi.product_id = p.id " +
                            "JOIN orders o ON oi.order_id = o.id " +
                            "WHERE o.status IS NULL OR o.status NOT IN ('CANCELLED','RETURNED','REFUNDED') " +
                            "GROUP BY p.name ORDER BY amount DESC LIMIT 10"));
        } catch (Exception e) {
            result.put("productSales", List.of());
        }
        try {
            result.put("inquiry", jdbcTemplate.queryForList(
                    "SELECT COALESCE(status, 'PENDING') as status, COUNT(*) as count FROM inquiries GROUP BY status"));
        } catch (Exception e) {
            result.put("inquiry", List.of());
        }
        result.put("status", "SUCCESS");
        return ResponseEntity.ok(result);
    }
}
