package com.nexis.web.controller;

import com.nexis.web.entity.ItemEntity;
import com.nexis.web.entity.HostTemplateEntity;
import com.nexis.web.entity.ItemDataEntity;
import com.nexis.web.repository.HostTemplateRepository;
import com.nexis.web.repository.ItemDataRepository;
import com.nexis.web.repository.ItemRepository;
import com.nexis.web.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService            itemService;
    private final ItemRepository         itemRepository;
    private final ItemDataRepository     itemDataRepository;
    private final HostTemplateRepository hostTemplateRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ItemController(ItemService            itemService,
                          ItemRepository         itemRepository,
                          ItemDataRepository     itemDataRepository,
                          HostTemplateRepository hostTemplateRepository) {
        this.itemService            = itemService;
        this.itemRepository         = itemRepository;
        this.itemDataRepository     = itemDataRepository;
        this.hostTemplateRepository = hostTemplateRepository;
    }

    // 호스트 아이템 목록 조회
    @GetMapping("/host/{hostId}")
    public ResponseEntity<?> listByHost(@PathVariable("hostId") Long hostId) {
        return ResponseEntity.ok(itemService.getItemsByHost(hostId));
    }

    // 템플릿 원본 아이템 목록 조회 (hostId == null인 것만 — 호스트 복사본 제외)
    @GetMapping("/template/{templateId}")
    public ResponseEntity<?> listByTemplate(@PathVariable("templateId") Long templateId) {
        List<ItemEntity> result = itemService.getItemsByTemplate(templateId)
            .stream()
            .filter(i -> i.getHostId() == null)
            .toList();
        return ResponseEntity.ok(result);
    }

    // 아이템 생성 — 중복 itemKey 체크 후 저장, 템플릿 아이템이면 연결 호스트에도 자동 복사
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            String itemKey    = body.get("itemKey")    != null ? (String) body.get("itemKey") : null;
            Long   hostId     = body.get("hostId")     != null ? ((Number) body.get("hostId")).longValue()     : null;
            Long   templateId = body.get("templateId") != null ? ((Number) body.get("templateId")).longValue() : null;

            // itemKey 중복 체크
            if (itemKey != null && !itemKey.isBlank()) {
                if (templateId != null) {
                    boolean dup = itemService.getItemsByTemplate(templateId)
                        .stream().anyMatch(i -> itemKey.equals(i.getItemKey()));
                    if (dup)
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "An item with this itemKey already exists in the template."));
                }
                if (hostId != null) {
                    boolean dup = itemService.getItemsByHost(hostId)
                        .stream().anyMatch(i -> itemKey.equals(i.getItemKey()));
                    if (dup)
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "An item with this itemKey already exists on the host."));
                }
            }

            ItemEntity item  = buildFromBody(body, null);
            ItemEntity saved = itemService.save(item);

            // 템플릿 원본 아이템이면 연결된 호스트에도 복사
            if (templateId != null && hostId == null) {
                syncItemToLinkedHosts(saved, templateId);
            }

            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 아이템 수정 — 중복 체크 후 저장, 템플릿 원본이면 호스트 복사본도 동기화
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id,
                                    @RequestBody Map<String, Object> body) {
        return itemService.getById(id).map(item -> {
            try {
                if (body.containsKey("itemKey")) {
                    String newKey = (String) body.get("itemKey");
                    if (newKey != null && !newKey.isBlank() && !newKey.equals(item.getItemKey())) {
                        if (item.getTemplateId() != null) {
                            boolean dup = itemService.getItemsByTemplate(item.getTemplateId())
                                .stream().anyMatch(x -> !x.getId().equals(id) && newKey.equals(x.getItemKey()));
                            if (dup)
                                return ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error", "An item with this itemKey already exists in the template."));
                        }
                        if (item.getHostId() != null) {
                            boolean dup = itemService.getItemsByHost(item.getHostId())
                                .stream().anyMatch(x -> !x.getId().equals(id) && newKey.equals(x.getItemKey()));
                            if (dup)
                                return ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error", "An item with this itemKey already exists on the host."));
                        }
                    }
                }
                buildFromBody(body, item);
                itemService.save(item);
                // 템플릿 원본 수정 시 연결 호스트 복사본도 동기화 (interval 포함)
                if (item.getTemplateId() != null && item.getHostId() == null) {
                    syncItemUpdateToLinkedHosts(item);
                }
                return ResponseEntity.ok(Map.<String, Object>of("success", true));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // 아이템 삭제 — 수집 데이터(ItemData)도 함께 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        itemDataRepository.deleteByItemId(id);
        itemService.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 가장 최근 수집값 단건 조회 (아이템 카드 현재값 표시용)
    // interval도 함께 반환해 프론트에서 갱신 주기 참고 가능
    @GetMapping("/{id}/latest")
    public ResponseEntity<?> latestValue(@PathVariable("id") Long id) {
        return itemService.getById(id).map(item -> {
            return itemDataRepository.findTopByItemIdOrderByCollectedAtDesc(id)
                .map(d -> {
                    String unitLabel = item.getUnitLabel();
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("value",     d.getValue() != null ? Math.round(d.getValue() * 100.0) / 100.0 : null);
                    result.put("time",      d.getCollectedAt() != null ? d.getCollectedAt().format(FMT) : "");
                    result.put("unitLabel", unitLabel != null ? unitLabel : "");
                    result.put("interval",  item.getInterval()); // 수집 주기 (프론트 갱신 참고용)
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    // 수집된 데이터가 아직 없는 경우 빈 응답
                    Map<String, Object> empty = new java.util.HashMap<>();
                    empty.put("value",     null);
                    empty.put("time",      "");
                    empty.put("unitLabel", "");
                    empty.put("interval",  item.getInterval());
                    return ResponseEntity.ok(empty);
                });
        }).orElse(ResponseEntity.notFound().build());
    }

    // 차트용 시계열 데이터 조회 — minutes 파라미터로 조회 범위 지정 (기본 60분)
    @GetMapping("/{id}/chart")
    public ResponseEntity<?> chartData(@PathVariable("id") Long id,
                                       @RequestParam(name = "minutes", defaultValue = "60") int minutes) {
        return itemService.getById(id).map(item -> {
            LocalDateTime from = LocalDateTime.now().minusMinutes(minutes);
            List<ItemDataEntity> dataList = itemDataRepository
                .findByItemIdAndCollectedAtAfterOrderByCollectedAtAsc(id, from);

            String unitLabel = item.getUnitLabel();
            List<Map<String, Object>> result = new ArrayList<>();
            for (ItemDataEntity d : dataList) {
                if (d.getValue() != null) {
                    result.add(Map.of(
                        "time",      d.getCollectedAt() != null ? d.getCollectedAt().format(FMT) : "",
                        "value",     Math.round(d.getValue() * 100.0) / 100.0,
                        "unitLabel", unitLabel != null ? unitLabel : ""
                    ));
                }
            }
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    // 템플릿 아이템 신규 생성 시 이미 연결된 호스트에 복사 (interval 포함)
    private void syncItemToLinkedHosts(ItemEntity tplItem, Long templateId) {
        List<HostTemplateEntity> links = hostTemplateRepository.findByTemplateId(templateId);
        for (HostTemplateEntity link : links) {
            Long hostId = link.getHostId();
            List<ItemEntity> hostItems = itemService.getItemsByHost(hostId);
            boolean exists = hostItems.stream()
                .anyMatch(i -> tplItem.getItemKey().equals(i.getItemKey()));
            if (!exists) {
                itemRepository.save(copyForHost(tplItem, hostId, templateId));
            }
        }
    }

    // 템플릿 아이템 수정 시 연결된 호스트의 복사본도 동기화 (interval 포함)
    private void syncItemUpdateToLinkedHosts(ItemEntity tplItem) {
        Long templateId = tplItem.getTemplateId();
        List<HostTemplateEntity> links = hostTemplateRepository.findByTemplateId(templateId);
        for (HostTemplateEntity link : links) {
            Long hostId = link.getHostId();
            List<ItemEntity> hostItems = itemService.getItemsByHost(hostId);
            hostItems.stream()
                .filter(i -> tplItem.getItemKey().equals(i.getItemKey())
                          && templateId.equals(i.getSourceTemplateId()))
                .findFirst()
                .ifPresent(existing -> {
                    existing.setName(tplItem.getName());
                    existing.setMetric(tplItem.getMetric());
                    existing.setValueType(tplItem.getValueType());
                    existing.setUnit(tplItem.getUnit());
                    existing.setUnitDisplay(tplItem.getUnitDisplay());
                    existing.setDescription(tplItem.getDescription());
                    existing.setEnabled(tplItem.isEnabled());
                    existing.setInterval(tplItem.getInterval()); // 수집 주기도 동기화
                    itemRepository.save(existing);
                });
        }
    }

    // 템플릿 아이템을 호스트용으로 복사 (interval 포함, sourceTemplateId로 출처 기록)
    private ItemEntity copyForHost(ItemEntity src, Long hostId, Long sourceTemplateId) {
        ItemEntity n = new ItemEntity();
        n.setName(src.getName());
        n.setItemKey(src.getItemKey());
        n.setMetric(src.getMetric());
        n.setValueType(src.getValueType());
        n.setUnit(src.getUnit());
        n.setUnitDisplay(src.getUnitDisplay());
        n.setDescription(src.getDescription());
        n.setEnabled(src.isEnabled());
        n.setInterval(src.getInterval()); // 수집 주기 복사
        n.setHostId(hostId);
        n.setTemplateId(null);
        n.setSourceTemplateId(sourceTemplateId);
        return n;
    }

    // 요청 body에서 아이템 필드를 읽어 엔티티에 세팅 (null이면 새 엔티티, 아니면 기존 수정)
    private ItemEntity buildFromBody(Map<String, Object> body, ItemEntity existing) {
        ItemEntity item = existing != null ? existing : new ItemEntity();
        if (body.containsKey("name"))        item.setName((String) body.get("name"));
        if (body.containsKey("metric"))      item.setMetric((String) body.get("metric"));
        if (body.containsKey("valueType"))   item.setValueType((String) body.get("valueType"));
        if (body.containsKey("unit"))        item.setUnit((String) body.get("unit"));
        if (body.containsKey("description")) item.setDescription((String) body.get("description"));
        if (body.containsKey("enabled"))     item.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("hostId") && body.get("hostId") != null)
            item.setHostId(((Number) body.get("hostId")).longValue());
        if (body.containsKey("templateId") && body.get("templateId") != null)
            item.setTemplateId(((Number) body.get("templateId")).longValue());
        if (body.containsKey("itemKey") && body.get("itemKey") != null
                && !((String) body.get("itemKey")).isBlank()) {
            item.setItemKey((String) body.get("itemKey"));
        } else if (existing == null) {
            item.setItemKey(null);
        }
        if (body.containsKey("unitDisplay"))
            item.setUnitDisplay((String) body.get("unitDisplay"));

        // interval 파싱 — 0 이하 입력은 기본값 60초로 보정
        if (body.containsKey("interval") && body.get("interval") != null) {
            int interval = ((Number) body.get("interval")).intValue();
            item.setInterval(interval > 0 ? interval : 60);
        }

        return item;
    }
}