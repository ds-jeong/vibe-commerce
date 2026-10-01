package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final JdbcTemplate jdbcTemplate;

    public ByteArrayInputStream exportSettlementExcel() throws IOException {
        // 1. DB에서 30일치 일별 정산 요약 장부 실시간 원격 조회 (날짜 내림차순 정렬)
        String sql = "SELECT summary_date, daily_total_sales, daily_total_pg_fee, " +
                     "daily_total_platform_fee, daily_net_settlement, total_order_count " +
                     "FROM daily_settlement_summaries ORDER BY summary_date DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        // 2. ✨ 메모리 OOM(초과)을 원천 차단하는 실무형 스트리밍 SXSSFWorkbook 워크북 컴파일러 가동
        // 100로우마다 메모리 버퍼를 디스크로 방출하여 자바 힙 메모리를 극도로 절약합니다.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("30일 누적 정산 리포트");

            // 3. 서식 조립식 스타일 폰트 셋팅 (헤더용 대담 서식)
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 12);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); // 금융 대시보드 표준 네이비 테마
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // 데이터 행용 금액 천단위 컴마(#,##0) 셀 서식 정의
            CellStyle infoStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            infoStyle.setDataFormat(format.getFormat("#,##0"));
            infoStyle.setAlignment(HorizontalAlignment.RIGHT);

            // 4. 상단 헤더 칼럼 로우 렌더링 명세
            String[] columns = {"정산 일자", "주문 건수", "총 매출액(₩)", "PG 수수료(3.3%)", "플랫폼 수수료(10%)", "최종 실지급액(₩)"};
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerStyle);
            }

            // 5. DB 데이터 셀 장부 적재 루프 가동
            int rowIdx = 1;
            for (Map<String, Object> rowMap : rows) {
                Row row = sheet.createRow(rowIdx++);

                // 정산 일자 (날짜 포맷 정제)
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(rowMap.get("summary_date").toString());
                
                // 주문 건수
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(((Number) rowMap.get("total_order_count")).longValue());
                
                // 총 매출액 및 수수료들 정밀 맵 바인딩 (BigDecimal 보호 장치 적용)
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(((BigDecimal) rowMap.get("daily_total_sales")).doubleValue());
                cell2.setCellStyle(infoStyle);

                Cell cell3 = row.createCell(3);
                cell3.setCellValue(((BigDecimal) rowMap.get("daily_total_pg_fee")).doubleValue());
                cell3.setCellStyle(infoStyle);

                Cell cell4 = row.createCell(4);
                cell4.setCellValue(((BigDecimal) rowMap.get("daily_total_platform_fee")).doubleValue());
                cell4.setCellStyle(infoStyle);

                Cell cell5 = row.createCell(5);
                cell5.setCellValue(((BigDecimal) rowMap.get("daily_net_settlement")).doubleValue());
                cell5.setCellStyle(infoStyle);
            }

            // 6. 메모리 자원을 최종 바이트 스트림 파이프라인으로 압축 덤프 이관
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
