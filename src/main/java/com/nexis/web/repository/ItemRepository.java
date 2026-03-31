package com.nexis.web.repository;

import com.nexis.web.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    // 특정 호스트의 아이템 목록 (생성일 오름차순)
    List<ItemEntity> findByHostIdOrderByCreatedAtAsc(Long hostId);

    // 특정 템플릿의 아이템 목록 (원본 + 호스트 복사본 모두 포함)
    // hostId == null 필터링은 서비스/컨트롤러에서 처리
    List<ItemEntity> findByTemplateIdOrderByCreatedAtAsc(Long templateId);

    // 호스트에서 특정 템플릿으로 복사된 아이템 전체 삭제 (clear 모드용)
    void deleteByHostIdAndTemplateId(Long hostId, Long templateId);
}