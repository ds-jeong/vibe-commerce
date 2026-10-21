package com.example.ecommerce.domain;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products", indexes = {@Index(name = "idx_product_created_at", columnList = "createdAt")})
@Getter @Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private BigDecimal price;
    private Integer stockQuantity;
    @Column(length = 2048)
    private String imageUrl;
    @Column(columnDefinition = "TEXT")
    private String description;
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SaleStatus saleStatus = SaleStatus.ON_SALE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnore
    private Category category;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long categoryId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String categoryName;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductImage> detailImages;
}
