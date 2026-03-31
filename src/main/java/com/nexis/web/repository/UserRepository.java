package com.nexis.web.repository;

import com.nexis.web.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // 유저명 오름차순 전체 조회 (관리 페이지 목록용)
    List<UserEntity> findAllByOrderByUsernameAsc();

    // 유저명 중복 체크 (회원가입/추가 시 사용)
    boolean existsByUsername(String username);

    // 로그인 시 Spring Security가 UserDetailsService를 통해 호출
    Optional<UserEntity> findByUsername(String username);
}