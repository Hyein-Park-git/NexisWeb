package com.nexis.web.repository;

import com.nexis.web.entity.UserGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface UserGroupMemberRepository extends JpaRepository<UserGroupMemberEntity, Long> {

    // 특정 유저가 속한 그룹 목록
    List<UserGroupMemberEntity> findByUserId(Long userId);

    // 특정 그룹에 속한 멤버 목록
    List<UserGroupMemberEntity> findByGroupId(Long groupId);

    // 유저 삭제 시 그룹 배정도 함께 삭제
    @Transactional
    void deleteByUserId(Long userId);

    // 그룹 삭제 시 멤버 배정도 함께 삭제
    @Transactional
    void deleteByGroupId(Long groupId);

    // 이미 그룹에 배정된 유저인지 중복 체크
    boolean existsByUserIdAndGroupId(Long userId, Long groupId);
}