package com.example.ecommerce.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_settlement_summaries", indexes = {@Index(name = "idx_summary_date", columnList = "summaryDate")})
@Getter @Setter
@NoArgsConstructor
public class DailySettlementSummary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private LocalDate summaryDate;
    private BigDecimal dailyTotalSales;
    private BigDecimal dailyTotalPgFee;
    private BigDecimal dailyTotalPlatformFee;
    private BigDecimal dailyNetSettlement;
    private Long totalOrderCount;
}
