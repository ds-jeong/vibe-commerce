package com.example.ecommerce.global;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor // ✨ 내부 주입 객체(JwtProvider)의 런타임 생성자 자동 포워딩
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // 1. HTTP 인바운드 요청 헤더에서 'Authorization' 전표 추출
        String bearerToken = request.getHeader("Authorization");
        String token = null;

        // 2. 'Bearer ' 접두사를 떼어내고 순수 JWT 암호화 스트링만 슬라이싱 수급
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }

        // 3. 추출한 토큰의 물리적 변조 및 시간 전표 만료 여부를 1단계 엔진으로 검증
        if (token != null && jwtProvider.validateToken(token)) {
            String userKey = jwtProvider.getUserKey(token);
            String role = jwtProvider.getRole(token); // 예: "ADMIN" 또는 "USER"

            // 4. 스프링 시큐리티 규격에 부합하는 가상 권한 객체(SimpleGrantedAuthority) 바인딩
            // 스프링 내부 표준 통제를 위해 앞에 "ROLE_" 접두사를 빌트인 연동합니다.
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userKey, null, Collections.singletonList(authority));

            // 5. 시큐리티 컨텍스트 레이어에 인증 전표 최종 영속화 (인증 프리패스 락 잠금 해제)
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 6. 검문 마감 후 다음 시큐리티 필터 체인 파이프라인으로 컨텍스트 이관 포워딩
        filterChain.doFilter(request, response);
    }
}
