package com.nexis.web.repository;

import com.nexis.web.entity.UserGroupHostAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface UserGroupHostAccessRepository extends JpaRepository<UserGroupHostAccessEntity, Long> {

    // 특정 유저 그룹이 접근 가능한 호스트 그룹 목록
    List<UserGroupHostAccessEntity> findByGroupId(Long groupId);

    // 접근 권한 중복 체크
    boolean existsByGroupIdAndHostGroupId(Long groupId, Long hostGroupId);

    // @Modifying: SELECT가 아닌 DML(DELETE/UPDATE) 쿼리임을 명시
    // 메서드 이름 규칙으로 자동 생성이 안 되는 경우 @Query로 직접 작성
    @Modifying
    @Transactional
    @Query("DELETE FROM UserGroupHostAccessEntity a WHERE a.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}