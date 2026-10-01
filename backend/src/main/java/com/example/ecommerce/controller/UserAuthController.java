package com.example.ecommerce.controller;

import com.example.ecommerce.domain.User;
import com.example.ecommerce.domain.Role;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.global.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserAuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public UserAuthController(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping("/check-id")
    public ResponseEntity<Map<String, Object>> checkDuplicateId(@RequestParam String userKey) {
        Map<String, Object> response = new HashMap<>();
        
        // 🔒 [ID 정규식 검수] 영문 소문자/숫자 조합 4~12자리 검증
        if (!userKey.matches("^[a-z0-9]{4,12}$")) {
            response.put("status", "INVALID_FORMAT");
            response.put("message", "아이디는 영문 소문자와 숫자 조합의 4~12자리 규격이어야 합니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        boolean isDuplicate = userRepository.findByUserKey(userKey).isPresent();
        if (isDuplicate) {
            response.put("status", "DUPLICATE");
            response.put("message", "이미 사용 중인 아이디입니다.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        
        response.put("status", "AVAILABLE");
        response.put("message", "사용 가능한 아이디입니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> userSignup(@RequestBody Map<String, String> signupRequest) {
        String userKey = signupRequest.get("userKey");
        String password = signupRequest.get("password");
        String name = signupRequest.get("name");
        String phoneNumber = signupRequest.get("phoneNumber");
        String zipcode = signupRequest.get("zipcode");
        String roadAddress = signupRequest.get("roadAddress");
        String detailAddress = signupRequest.get("detailAddress");

        Map<String, Object> response = new HashMap<>();

        // 🛑 [백엔드 커널 최종 정규식 3중 가드레일 가동]
        if (!userKey.matches("^[a-z0-9]{4,12}$")) {
            response.put("status", "FAIL"); response.put("message", "ID 규격 위배");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        // 🔒 비밀번호 정규식: 영문, 숫자, 특수문자 최소 1개씩 포함 8~16자리
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$")) {
            response.put("status", "FAIL"); response.put("message", "비밀번호 복잡도 스펙 위배");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        // 🔒 연락처 정규식: 대한민국 휴대폰 번호 표준 포맷 (010으로 시작하는 10~11자리 대조)
        if (!phoneNumber.matches("^010\\d{3,4}\\d{4}$")) {
            response.put("status", "FAIL"); response.put("message", "연락처 문자 수식 정합성 위배");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        // 🔒 우편번호 검수: 새 우편번호 5자리 숫자 검증
        if (!zipcode.matches("^\\d{5}$")) {
            response.put("status", "FAIL"); response.put("message", "우편번호 전표 규격 위배");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userRepository.findByUserKey(userKey).isPresent()) {
            response.put("status", "FAIL"); response.put("message", "중복 검증 위배 침투");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User newUser = new User();
        newUser.setUserKey(userKey);
        newUser.setPassword(password);
        newUser.setName(name);
        newUser.setPhoneNumber(phoneNumber);
        newUser.setZipcode(zipcode);
        newUser.setRoadAddress(roadAddress);
        newUser.setDetailAddress(detailAddress);
        newUser.setRole(Role.USER); 

        userRepository.save(newUser);

        response.put("status", "SUCCESS");
        response.put("message", "종합 검수 통과 및 회원 정보 영구 저장 완수");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> userLogin(@RequestBody Map<String, String> loginRequest) {
        String userKey = loginRequest.get("userKey");
        String password = loginRequest.get("password");
        Map<String, Object> response = new HashMap<>();

        return userRepository.findByUserKey(userKey)
                .filter(user -> user.getPassword().equals(password))
                .map(user -> {
                    String token = jwtProvider.createToken(user.getUserKey(), user.getRole().name());
                    response.put("status", "SUCCESS");
                    response.put("accessToken", token);
                    response.put("role", user.getRole().name());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    response.put("status", "FAIL");
                    response.put("message", "자격 증명 정보가 불일치합니다.");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                });
    }
}
