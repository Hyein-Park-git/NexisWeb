package com.nexis.web.repository;

import com.nexis.web.entity.TriggerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TriggerRepository extends JpaRepository<TriggerEntity, Long> {

    // 특정 호스트의 트리거 목록 (생성일 오름차순)
    List<TriggerEntity> findByHostIdOrderByCreatedAtAsc(Long hostId);

    // 특정 아이템에 연결된 트리거 목록 (아이템 삭제 전 연관 트리거 확인용)
    List<TriggerEntity> findByItemIdOrderByCreatedAtAsc(Long itemId);

    // 특정 템플릿의 트리거 목록 (원본 + 호스트 복사본 모두 포함)
    List<TriggerEntity> findByTemplateIdOrderByCreatedAtAsc(Long templateId);

    // 활성화된 트리거만 조회 (에이전트 수집 데이터 평가 시 사용)
    List<TriggerEntity> findByEnabledTrueOrderByCreatedAtAsc();

    // clear 모드: 특정 호스트에서 특정 템플릿으로 복사된 트리거 삭제
    void deleteByHostIdAndTemplateId(Long hostId, Long templateId);
}