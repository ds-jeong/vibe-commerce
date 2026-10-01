package com.example.ecommerce.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // 💡 OrderService의 컴파일 및 구동 에러를 해결하기 위해 BCrypt 암호화 빈을 선언합니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // 🔓 1. 비회원/회원 공통 전면 개방 채널 (상품 조회, 로그인, 비회원 주문 및 결제 검증 채널 전면 개방)
                .requestMatchers(
                    "/api/products/**",
                    "/uploads/**",
                    "/api/admin/login", 
                    "/api/user/login",
                    "/api/orders/place",   // 💡 비회원 주문서 생성을 위해 오픈
                    "/api/orders/verify",  // 💡 카카오페이 결제 검증을 위해 오픈
                    "/api/orders/non-user/**" // 💡 비회원 주문조회 로그인을 위해 오픈
                ).permitAll()
                
                // 🛑 2. 최고관리자 독점 채널 (정산 통계, 배치 스케줄러, 엑셀 다운로드)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // 🔐 3. 일반 회원 전용 채널 (회원 전용 장바구니 및 회원 주문 내역 조회 권한 분리)
                .requestMatchers("/api/cart/**", "/api/orders/my", "/api/orders/*/cancel", "/api/orders/*/return-request", "/api/user/profile", "/api/user/profile/**", "/api/inquiries", "/api/inquiries/**").hasRole("USER")
                
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
