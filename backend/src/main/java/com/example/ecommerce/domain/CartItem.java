package com.example.ecommerce.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cart_items")
@Getter @Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username; // 소유주 회원 ID (엔지니어님의 User 엔티티 내 userKey와 100% 매핑 결합)

    @Column(nullable = false)
    private Long productId;  // 이관된 상품 고유 식별자 ID

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private int quantity;    // 비회원 상태에서 누적 담기한 수량

    public CartItem(String username, Long productId, String productName, Long price, int quantity) {
        this.username = username;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }
}
