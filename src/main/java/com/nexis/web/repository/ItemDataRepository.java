package com.nexis.web.repository;

import com.nexis.web.entity.ItemDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ItemDataRepository extends JpaRepository<ItemDataEntity, Long> {

    // 차트 데이터 조회 — 특정 아이템의 from 이후 데이터를 시간순으로
    List<ItemDataEntity> findByItemIdAndCollectedAtAfterOrderByCollectedAtAsc(Long itemId, LocalDateTime from);

    // 특정 아이템의 전체 이력 (최신순)
    List<ItemDataEntity> findByItemIdOrderByCollectedAtDesc(Long itemId);

    // @Query + Pageable: 최신 N건만 가져오는 커스텀 쿼리 (LIMIT 효과)
    // 예: findLatestByItemId(id, PageRequest.of(0, 1)) → 최신 1건
    @Query("SELECT d FROM ItemDataEntity d WHERE d.itemId = :itemId ORDER BY d.collectedAt DESC")
    List<ItemDataEntity> findLatestByItemId(
            @Param("itemId") Long itemId,
            org.springframework.data.domain.Pageable pageable);

    // 특정 호스트의 from 이후 수집 데이터 (hostname 기반 조회)
    @Query("SELECT d FROM ItemDataEntity d " +
           "WHERE d.hostname = :hostname AND d.collectedAt >= :from " +
           "ORDER BY d.collectedAt ASC")
    List<ItemDataEntity> findByHostnameAndCollectedAtAfter(
            @Param("hostname") String hostname,
            @Param("from") LocalDateTime from);

    // 여러 아이템의 최신값을 한 번에 조회 (Latest Data 목록 페이지 성능 최적화용)
    // 서브쿼리로 각 itemId의 MAX(collectedAt)인 행만 가져옴
    @Query("""
        SELECT d FROM ItemDataEntity d
        WHERE d.collectedAt = (
            SELECT MAX(d2.collectedAt) FROM ItemDataEntity d2 WHERE d2.itemId = d.itemId
        )
        AND d.itemId IN :itemIds
    """)
    List<ItemDataEntity> findLatestByItemIds(@Param("itemIds") List<Long> itemIds);

    // 특정 아이템의 가장 최근 수집값 1건 (아이템 카드의 현재값 표시용)
    Optional<ItemDataEntity> findTopByItemIdOrderByCollectedAtDesc(Long itemId);

    // 아이템 삭제 시 수집 데이터도 함께 삭제
    void deleteByItemId(Long itemId);

    // 최근 활성 호스트 목록 조회 (from 이후 데이터가 있는 hostname만)
    @Query("SELECT DISTINCT d.hostname FROM ItemDataEntity d WHERE d.collectedAt >= :from")
    List<String> findDistinctHostnamesByCollectedAtAfter(@Param("from") LocalDateTime from);

    // 특정 호스트의 마지막 수집 시각 조회 (에이전트 상태 모니터링용)
    @Query("SELECT MAX(d.collectedAt) FROM ItemDataEntity d WHERE d.hostname = :hostname")
    Optional<LocalDateTime> findLatestCollectedAtByHostname(@Param("hostname") String hostname);
}