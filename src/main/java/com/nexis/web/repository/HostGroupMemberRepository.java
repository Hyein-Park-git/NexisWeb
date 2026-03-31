package com.nexis.web.repository;

import com.nexis.web.entity.HostGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

// JpaRepository<엔티티, PK타입>을 상속하면
// save/findById/findAll/deleteById 등 기본 CRUD 메서드가 자동으로 생성됨
// 메서드 이름 규칙(findBy, existsBy, deleteBy...)만 맞추면 쿼리도 자동 생성
public interface HostGroupMemberRepository extends JpaRepository<HostGroupMemberEntity, Long> {

    // 특정 그룹에 속한 멤버(호스트) 목록
    List<HostGroupMemberEntity> findByGroupId(Long groupId);

    // 특정 호스트가 속한 그룹 목록
    List<HostGroupMemberEntity> findByHostId(Long hostId);

    // 이미 그룹에 속한 호스트인지 중복 체크
    boolean existsByGroupIdAndHostId(Long groupId, Long hostId);

    // @Transactional: 데이터 변경(delete/update)은 트랜잭션이 필요
    // Repository의 기본 save/delete는 자동 트랜잭션이지만,
    // 커스텀 delete 메서드는 직접 붙여줘야 함
    @Transactional
    void deleteByGroupId(Long groupId);

    @Transactional
    void deleteByHostId(Long hostId);
}