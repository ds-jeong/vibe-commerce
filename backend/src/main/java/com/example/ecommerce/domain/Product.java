package com.example.ecommerce.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {@Index(name = "idx_product_created_at", columnList = "createdAt")})
@Getter @Setter
@NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    @Column(columnDefinition = "TEXT")
    private String description;
    private LocalDateTime createdAt = LocalDateTime.now();
}
