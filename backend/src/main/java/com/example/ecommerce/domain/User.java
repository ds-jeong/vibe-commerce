package com.example.ecommerce.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String userKey;

    @JsonIgnore
    private String password;
    private String name;
    private String email;
    private String phoneNumber;

    // 🏡 [실물 배송지 추적을 위한 데이터베이스 3대 전표 컬럼 신설 명세]
    private String zipcode;       // 우편번호 (5자리 표준 전표)
    private String roadAddress;   // 도로명 기본 배송 주소
    private String detailAddress; // 상세 호수 및 아파트 동 명세


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // ✨ 상단 동일 패키지 내의 독립된 Role 객체를 정밀 자동 참조합니다.

    private LocalDateTime createdAt = LocalDateTime.now();

    // 💡 롬복 의존성 데드락을 영구 사살하는 순수 자바 뼈대 메서드
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getZipcode() { return zipcode; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }

    public String getRoadAddress() { return roadAddress; }
    public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }

    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
}
