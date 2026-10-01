package com.example.ecommerce.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Getter @Setter
@NoArgsConstructor
public class Refund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;
    private String pgCancelUid;
    @Column(nullable = false)
    private BigDecimal refundAmount;
    private BigDecimal refundPgFee;
    private BigDecimal refundPlatformFee;
    private BigDecimal netRefundAmount;
    @Column(columnDefinition = "TEXT")
    private String refundReason;
    private LocalDateTime refundedAt = LocalDateTime.now();
}
