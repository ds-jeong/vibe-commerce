package com.example.ecommerce.repository;

import com.example.ecommerce.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // ✨ 엔지니어님의 고유 필드인 'userKey'를 팩트 대조하여 회원 전표를 스캔하는 정석 쿼리
    Optional<User> findByUserKey(String userKey);
}
