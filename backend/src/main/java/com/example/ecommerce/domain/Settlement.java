package com.example.ecommerce.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter @Setter
@NoArgsConstructor
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Orders order;
    private BigDecimal totalSalesAmount;
    private BigDecimal pgFee;
    private BigDecimal platformFee;
    private BigDecimal netSettlementAmount;
    private LocalDateTime settledAt = LocalDateTime.now();
}
