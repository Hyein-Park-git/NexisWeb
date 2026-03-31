package com.nexis.web.repository;

import com.nexis.web.entity.HostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HostRepository extends JpaRepository<HostEntity, Long> {

    // 호스트명 오름차순 전체 조회
    List<HostEntity> findAllByOrderByHostnameAsc();

    // 에이전트에서 데이터 수신 시 hostname으로 호스트 조회
    Optional<HostEntity> findByHostname(String hostname);

    // 호스트 등록 전 중복 체크
    boolean existsByHostname(String hostname);

    // enabled = true인 호스트 수 (통계용)
    long countByEnabledTrue();

    // agentActive = true인 호스트 수 (온라인 호스트 수 통계용)
    long countByAgentActiveTrue();
}