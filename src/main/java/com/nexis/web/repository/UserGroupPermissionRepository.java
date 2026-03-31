package com.nexis.web.repository;

import com.nexis.web.entity.UserGroupPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface UserGroupPermissionRepository extends JpaRepository<UserGroupPermissionEntity, Long> {

    // 특정 그룹의 전체 권한 목록 조회
    List<UserGroupPermissionEntity> findByGroupId(Long groupId);

    // 특정 그룹 + 리소스로 단건 조회 (e.g. groupId=1, resource="monitoring")
    Optional<UserGroupPermissionEntity> findByGroupIdAndResource(Long groupId, String resource);

    // 권한 저장 전 기존 권한 전체 삭제 (replace 방식으로 저장)
    @Modifying
    @Transactional
    @Query("DELETE FROM UserGroupPermissionEntity p WHERE p.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}