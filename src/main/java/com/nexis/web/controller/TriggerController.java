package com.nexis.web.controller;

import com.nexis.web.entity.HostTemplateEntity;
import com.nexis.web.entity.ItemEntity;
import com.nexis.web.entity.TriggerEntity;
import com.nexis.web.repository.HostTemplateRepository;
import com.nexis.web.repository.ItemRepository;
import com.nexis.web.repository.ProblemRepository;
import com.nexis.web.repository.TriggerRepository;
import com.nexis.web.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/triggers")
public class TriggerController {

    private final TriggerRepository      triggerRepository;
    private final ItemRepository         itemRepository;
    private final ProblemRepository      problemRepository;
    private final HostTemplateRepository hostTemplateRepository;
    private final ItemService            itemService;

    public TriggerController(TriggerRepository      triggerRepository,
                             ItemRepository         itemRepository,
                             ProblemRepository      problemRepository,
                             HostTemplateRepository hostTemplateRepository,
                             ItemService            itemService) {
        this.triggerRepository      = triggerRepository;
        this.itemRepository         = itemRepository;
        this.problemRepository      = problemRepository;
        this.hostTemplateRepository = hostTemplateRepository;
        this.itemService            = itemService;
    }

    // 호스트 소속 트리거 목록
    @GetMapping("/host/{hostId}")
    public ResponseEntity<?> listByHost(@PathVariable("hostId") Long hostId) {
        return ResponseEntity.ok(triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId));
    }

    // 템플릿 원본 트리거 목록 (hostId == null인 것만)
    @GetMapping("/template/{templateId}")
    public ResponseEntity<?> listByTemplate(@PathVariable("templateId") Long templateId) {
        List<TriggerEntity> result = triggerRepository.findByTemplateIdOrderByCreatedAtAsc(templateId)
            .stream()
            .filter(t -> t.getHostId() == null) // 원본만
            .toList();
        return ResponseEntity.ok(result);
    }

    // 트리거 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") Long id) {
        return triggerRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // 트리거 생성 — 중복 이름 체크 후 저장, 템플릿 원본이면 연결 호스트에도 자동 복사
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            String name       = (String) body.get("name");
            Long   hostId     = body.get("hostId")     != null ? ((Number) body.get("hostId")).longValue()     : null;
            Long   templateId = body.get("templateId") != null ? ((Number) body.get("templateId")).longValue() : null;

            if (body.get("itemId") == null)
                return ResponseEntity.badRequest().body(Map.of("error", "itemId is required."));

            // 이름 중복 체크
            if (templateId != null) {
                boolean dup = triggerRepository.findByTemplateIdOrderByCreatedAtAsc(templateId)
                    .stream().anyMatch(t -> t.getName().equals(name));
                if (dup)
                    return ResponseEntity.badRequest().body(Map.of("error", "A trigger with this name already exists in the template."));
            }
            if (hostId != null) {
                boolean dup = triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId)
                    .stream().anyMatch(t -> t.getName().equals(name));
                if (dup)
                    return ResponseEntity.badRequest().body(Map.of("error", "A trigger with this name already exists on the host."));
            }

            TriggerEntity t = buildFromBody(body, null);
            triggerRepository.save(t);

            // 템플릿 원본 트리거면 연결된 호스트에도 복사
            if (templateId != null && hostId == null) {
                syncTriggerToLinkedHosts(t, templateId);
            }

            return ResponseEntity.ok(Map.of("success", true, "id", t.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 트리거 수정 — 중복 이름 체크 후 저장, 템플릿 원본이면 호스트 복사본도 동기화
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id,
                                    @RequestBody Map<String, Object> body) {
        return triggerRepository.findById(id).map(t -> {
            try {
                if (body.containsKey("name")) {
                    String newName = (String) body.get("name");
                    if (!newName.equals(t.getName())) {
                        if (t.getTemplateId() != null) {
                            boolean dup = triggerRepository.findByTemplateIdOrderByCreatedAtAsc(t.getTemplateId())
                                .stream().anyMatch(x -> !x.getId().equals(id) && x.getName().equals(newName));
                            if (dup)
                                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "A trigger with this name already exists in the template."));
                        }
                        if (t.getHostId() != null) {
                            boolean dup = triggerRepository.findByHostIdOrderByCreatedAtAsc(t.getHostId())
                                .stream().anyMatch(x -> !x.getId().equals(id) && x.getName().equals(newName));
                            if (dup)
                                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "A trigger with this name already exists on the host."));
                        }
                    }
                    t.setName(newName);
                }

                if (body.containsKey("itemId"))      t.setItemId(((Number) body.get("itemId")).longValue());
                if (body.containsKey("hostId"))      t.setHostId(body.get("hostId") != null ? ((Number) body.get("hostId")).longValue() : null);
                if (body.containsKey("templateId"))  t.setTemplateId(body.get("templateId") != null ? ((Number) body.get("templateId")).longValue() : null);
                if (body.containsKey("func"))        t.setFunc((String) body.get("func"));
                if (body.containsKey("operator"))    t.setOperator((String) body.get("operator"));
                if (body.containsKey("threshold"))   t.setThreshold(((Number) body.get("threshold")).doubleValue());
                if (body.containsKey("expression"))  t.setExpression((String) body.get("expression"));
                if (body.containsKey("severity"))    t.setSeverity((String) body.get("severity"));
                if (body.containsKey("description")) t.setDescription((String) body.get("description"));
                if (body.containsKey("enabled"))     t.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
                triggerRepository.save(t);

                // 템플릿 원본 수정 시 연결 호스트 복사본도 동기화
                if (t.getTemplateId() != null && t.getHostId() == null) {
                    syncTriggerUpdateToLinkedHosts(t);
                }

                return ResponseEntity.ok(Map.<String, Object>of("success", true));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // 트리거 삭제 — 연관된 Problem도 함께 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        try {
            problemRepository.deleteByTriggerId(id); // Problem 먼저 삭제
            triggerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 템플릿 트리거 신규 생성 시 이미 연결된 호스트에 복사
    private void syncTriggerToLinkedHosts(TriggerEntity tplTrigger, Long templateId) {
        ItemEntity srcItem = itemRepository.findById(tplTrigger.getItemId()).orElse(null);
        if (srcItem == null) return;

        List<HostTemplateEntity> links = hostTemplateRepository.findByTemplateId(templateId);
        for (HostTemplateEntity link : links) {
            Long hostId = link.getHostId();
            // 호스트에서 같은 itemKey를 가진 아이템 찾기
            Long targetItemId = itemService.getItemsByHost(hostId).stream()
                .filter(i -> srcItem.getItemKey().equals(i.getItemKey()))
                .map(ItemEntity::getId)
                .findFirst().orElse(null);
            if (targetItemId == null) continue;

            boolean exists = triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId)
                .stream().anyMatch(t -> t.getName().equals(tplTrigger.getName()));
            if (!exists) {
                triggerRepository.save(copyForHost(tplTrigger, hostId, targetItemId, templateId));
            }
        }
    }

    // 템플릿 트리거 수정 시 연결된 호스트의 복사본 동기화
    private void syncTriggerUpdateToLinkedHosts(TriggerEntity tplTrigger) {
        Long templateId = tplTrigger.getTemplateId();
        ItemEntity srcItem = itemRepository.findById(tplTrigger.getItemId()).orElse(null);
        if (srcItem == null) return;

        List<HostTemplateEntity> links = hostTemplateRepository.findByTemplateId(templateId);
        for (HostTemplateEntity link : links) {
            Long hostId = link.getHostId();
            Long targetItemId = itemService.getItemsByHost(hostId).stream()
                .filter(i -> srcItem.getItemKey().equals(i.getItemKey()))
                .map(ItemEntity::getId)
                .findFirst().orElse(null);
            if (targetItemId == null) continue;

            // sourceTemplateId로 출처가 같은 복사본 찾기
            Optional<TriggerEntity> existing = triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId)
                .stream()
                .filter(t -> tplTrigger.getName().equals(t.getName())
                          && templateId.equals(t.getSourceTemplateId()))
                .findFirst();

            if (existing.isPresent()) {
                TriggerEntity e = existing.get();
                e.setItemId(targetItemId);
                e.setFunc(tplTrigger.getFunc()); e.setOperator(tplTrigger.getOperator());
                e.setThreshold(tplTrigger.getThreshold()); e.setExpression(tplTrigger.getExpression());
                e.setSeverity(tplTrigger.getSeverity()); e.setEnabled(tplTrigger.isEnabled());
                e.setDescription(tplTrigger.getDescription());
                triggerRepository.save(e);
            }
        }
    }

    private TriggerEntity copyForHost(TriggerEntity src, Long hostId, Long newItemId, Long sourceTemplateId) {
        TriggerEntity n = new TriggerEntity();
        n.setName(src.getName()); n.setItemId(newItemId); n.setHostId(hostId);
        n.setTemplateId(null); n.setSourceTemplateId(sourceTemplateId);
        n.setFunc(src.getFunc()); n.setOperator(src.getOperator()); n.setThreshold(src.getThreshold());
        n.setExpression(src.getExpression()); n.setSeverity(src.getSeverity());
        n.setEnabled(src.isEnabled()); n.setDescription(src.getDescription());
        return n;
    }

    // 요청 body에서 트리거 필드를 읽어 엔티티에 세팅
    private TriggerEntity buildFromBody(Map<String, Object> body, TriggerEntity existing) {
        TriggerEntity t = existing != null ? existing : new TriggerEntity();
        if (body.containsKey("name"))        t.setName((String) body.get("name"));
        if (body.containsKey("itemId"))      t.setItemId(((Number) body.get("itemId")).longValue());
        if (body.containsKey("hostId") && body.get("hostId") != null)
            t.setHostId(((Number) body.get("hostId")).longValue());
        if (body.containsKey("templateId") && body.get("templateId") != null)
            t.setTemplateId(((Number) body.get("templateId")).longValue());
        if (body.containsKey("func"))        t.setFunc((String) body.get("func"));
        if (body.containsKey("operator"))    t.setOperator((String) body.get("operator"));
        if (body.containsKey("threshold"))   t.setThreshold(((Number) body.get("threshold")).doubleValue());
        if (body.containsKey("expression"))  t.setExpression((String) body.get("expression"));
        if (body.containsKey("severity"))    t.setSeverity((String) body.get("severity"));
        if (body.containsKey("description")) t.setDescription((String) body.get("description"));
        if (body.containsKey("enabled"))     t.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("duration"))
            t.setDuration(body.get("duration") != null ? ((Number) body.get("duration")).intValue() : 0);
        return t;
    }
}