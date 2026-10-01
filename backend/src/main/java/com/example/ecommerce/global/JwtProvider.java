package com.example.ecommerce.global;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    // ✨ 256비트 이상의 대칭키 암호화를 만족하는 최고관리자 전용 특수 비밀 서명 키 스펙
    private final String secretPlain = "VibeCommerceSecretKeySignaturesEnterpriseBackOfficeSystemSecuritySecureTokenLongPlainString256";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(secretPlain.getBytes(StandardCharsets.UTF_8));
    
    // AS-IS: private final long tokenValidityInMilliseconds = 24 * 60 * 60 * 1000M;
    // TO-BE: 자바 표준 Long 리터럴 명세 규격인 'L' 지시어로 보정 완료합니다.
    private final long tokenValidityInMilliseconds = 24 * 60 * 60 * 1000L; // ✨ M을 L로 수정 완료!
    
    /**
     * 최고관리자 식별자 키(UserKey)와 권한(Role)을 내부 클레임에 복합 바인딩하여 토큰 발행
     */
    public String createToken(String userKey, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + this.tokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(userKey)
                .claim("role", role)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey) // 최신 io.jsonwebtoken 규격 암호화 서명
                .compact();
    }

    /**
     * 인입된 토큰을 비밀키로 역복호화 분해하여 유저 식별 식별자 추출 (Subject 검출)
     */
    public String getUserKey(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 인입된 토큰 내부의 권한 스트링 문자열 덤프 수급
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 토큰의 물리적 변조 유무 및 만료 연도 시간 전표의 정합성을 최종 팩트 체크 검증
     */
    public boolean validateToken(String token) {
        try {
            return !getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false; // 변조되었거나 시간 전표가 끊긴 토큰은 즉각 가공 처리 차단
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
