package com.nexis.web.repository;

import com.nexis.web.entity.ProblemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<ProblemEntity, Long> {

    // 상태(PROBLEM/ACKNOWLEDGED/RESOLVED)별 목록 (최신순)
    List<ProblemEntity> findByStatusOrderByStartedAtDesc(String status);

    // 특정 호스트의 특정 상태 문제 목록
    List<ProblemEntity> findByHostIdAndStatusOrderByStartedAtDesc(Long hostId, String status);

    // 트리거 ID + 상태로 단건 조회 (중복 Problem 생성 방지용)
    Optional<ProblemEntity> findByTriggerIdAndStatus(Long triggerId, String status);

    // 트리거 삭제 시 관련 Problem도 함께 삭제
    void deleteByTriggerId(Long triggerId);

    // 상태별 문제 수 카운트 (대시보드 통계용)
    long countByStatus(String status);

    // 상태 + severity 조합 카운트
    long countByStatusAndSeverity(String status, String severity);

    // 전체 이력 조회 (history 탭)
    List<ProblemEntity> findAllByOrderByStartedAtDesc();

    // 최근 100건만 조회 (성능 제한)
    List<ProblemEntity> findTop100ByOrderByStartedAtDesc();

    // from 이후 발생한 문제 목록 (ageLess 필터용)
    List<ProblemEntity> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime from);

    // 상태 + 시간 필터 조합
    List<ProblemEntity> findByStatusAndStartedAtAfterOrderByStartedAtDesc(String status, LocalDateTime from);

    // 트리거 이름 또는 expression으로 키워드 검색 (LIKE, 대소문자 무시)
    @Query("SELECT p FROM ProblemEntity p WHERE " +
           "LOWER(p.triggerName) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           "LOWER(p.expression)  LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<ProblemEntity> findByTriggerKeyword(@Param("kw") String keyword);
}