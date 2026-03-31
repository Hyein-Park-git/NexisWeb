package com.nexis.web.service;

import com.nexis.web.entity.ItemEntity;
import com.nexis.web.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    // 특정 호스트의 아이템 목록 (생성일 오름차순)
    public List<ItemEntity> getItemsByHost(Long hostId) {
        return itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
    }

    // 특정 템플릿의 아이템 목록 (원본 + 호스트 복사본 모두 포함)
    public List<ItemEntity> getItemsByTemplate(Long templateId) {
        return itemRepository.findByTemplateIdOrderByCreatedAtAsc(templateId);
    }

    // 단건 조회
    public Optional<ItemEntity> getById(Long id) {
        return itemRepository.findById(id);
    }

    // 아이템 저장 — itemKey 자동 생성, metric/valueType 역추론, interval 보정 포함
    public ItemEntity save(ItemEntity item) {
        // itemKey가 없으면 "metric.valueType" 형식으로 자동 생성
        // 예: metric="cpu", valueType="usage" → itemKey="cpu.usage"
        if (item.getItemKey() == null || item.getItemKey().isBlank()) {
            item.setItemKey(item.getMetric() + "." + item.getValueType());
        }

        resolveMetricFromKey(item); // itemKey로 metric/valueType 역추론

        // interval이 0 이하면 기본값 60초로 보정 (잘못된 입력 방어)
        if (item.getInterval() <= 0) {
            item.setInterval(60);
        }

        return itemRepository.save(item);
    }

    // 아이템 삭제
    public void delete(Long id) {
        itemRepository.deleteById(id);
    }

    // itemKey가 있는데 metric/valueType이 비어있으면 key에서 역추론
    // 예: "cpu.usage" → metric="cpu", valueType="usage"
    private void resolveMetricFromKey(ItemEntity item) {
        String key = item.getItemKey();
        if (key == null) return;

        // 이미 둘 다 있으면 역추론 불필요
        if (item.getMetric()    != null && !item.getMetric().isBlank()
         && item.getValueType() != null && !item.getValueType().isBlank()) return;

        // "." 기준으로 최대 2개 분리
        String[] parts = key.split("\\.", 2);
        if (parts.length == 2) {
            if (item.getMetric()    == null || item.getMetric().isBlank())    item.setMetric(parts[0]);
            if (item.getValueType() == null || item.getValueType().isBlank()) item.setValueType(parts[1]);
        }
    }
}