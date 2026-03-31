package com.nexis.web.repository;

import com.nexis.web.entity.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroupEntity, Long> {

    // 이름 오름차순 전체 조회 (목록 페이지용)
    List<UserGroupEntity> findAllByOrderByNameAsc();

    // 그룹 이름 중복 체크
    boolean existsByName(String name);

    // 이름으로 단건 조회 (Default Group 찾기 등에 사용)
    Optional<UserGroupEntity> findByName(String name);
}