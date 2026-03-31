package com.nexis.web.controller;

import com.nexis.web.entity.*;
import com.nexis.web.repository.*;
import com.nexis.web.service.ItemService;
import com.nexis.web.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRepository        templateRepository;
    private final HostRepository            hostRepository;
    private final HostTemplateRepository    hostTemplateRepository;
    private final ItemRepository            itemRepository;
    private final TriggerRepository         triggerRepository;
    private final ProblemRepository         problemRepository;
    private final ItemService               itemService;
    private final PermissionService         permissionService;
    private final HostGroupMemberRepository hostGroupMemberRepository;

    public TemplateController(TemplateRepository        templateRepository,
                              HostRepository            hostRepository,
                              HostTemplateRepository    hostTemplateRepository,
                              ItemRepository            itemRepository,
                              TriggerRepository         triggerRepository,
                              ProblemRepository         problemRepository,
                              ItemService               itemService,
                              PermissionService         permissionService,
                              HostGroupMemberRepository hostGroupMemberRepository) {
        this.templateRepository        = templateRepository;
        this.hostRepository            = hostRepository;
        this.hostTemplateRepository    = hostTemplateRepository;
        this.itemRepository            = itemRepository;
        this.triggerRepository         = triggerRepository;
        this.problemRepository         = problemRepository;
        this.itemService               = itemService;
        this.permissionService         = permissionService;
        this.hostGroupMemberRepository = hostGroupMemberRepository;
    }

    // Operator 권한 체크 — 허용된 호스트 그룹에 속하는지 확인
    private boolean isHostAllowed(Long hostId) {
        List<Long> allowedGroups = permissionService.getAllowedHostGroupIds();
        if (allowedGroups != null && allowedGroups.isEmpty()) return true; // Administrator
        if (allowedGroups == null) return false;
        List<Long> hostGroupIds = hostGroupMemberRepository.findByHostId(hostId)
            .stream().map(m -> m.getGroupId()).toList();
        return hostGroupIds.stream().anyMatch(allowedGroups::contains);
    }

    // 전체 템플릿 목록 조회 (이름 오름차순)
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(templateRepository.findAllByOrderByNameAsc());
    }

    // 템플릿 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") Long id) {
        return templateRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // 템플릿 추가 (이름 중복 체크 포함)
    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            String name = (String) body.get("name");
            if (name == null || name.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required."));
            if (templateRepository.existsByName(name))
                return ResponseEntity.badRequest().body(Map.of("error", "Template name already exists."));
            TemplateEntity t = new TemplateEntity();
            t.setName(name.trim());
            if (body.containsKey("description")) t.setDescription((String) body.get("description"));
            if (body.containsKey("osType"))      t.setOsType((String) body.get("osType"));
            templateRepository.save(t);
            return ResponseEntity.ok(Map.of("success", true, "id", t.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 템플릿 삭제 — 연결된 트리거/아이템/호스트 매핑도 함께 삭제 (@Transactional)
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            // 원본 트리거 삭제 (관련 Problem 포함)
            triggerRepository.findByTemplateIdOrderByCreatedAtAsc(id).stream()
                .filter(t -> t.getHostId() == null)
                .map(TriggerEntity::getId).toList()
                .forEach(tid -> {
                    problemRepository.deleteByTriggerId(tid);
                    triggerRepository.deleteById(tid);
                });
            // 원본 아이템 삭제
            itemRepository.findByTemplateIdOrderByCreatedAtAsc(id).stream()
                .filter(i -> i.getHostId() == null)
                .map(ItemEntity::getId).toList()
                .forEach(itemRepository::deleteById);
            hostTemplateRepository.deleteByTemplateId(id); // 호스트-템플릿 매핑 삭제
            templateRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 템플릿 적용 모달용 — 전체 호스트 목록 + 각 호스트에 적용된 템플릿 ID 목록 반환
    @GetMapping("/hosts-for-apply")
    public ResponseEntity<?> hostsForApply() {
        List<Map<String, Object>> result = hostRepository.findAll().stream()
            .map(h -> {
                List<Long> tplIds = hostTemplateRepository.findByHostId(h.getId())
                    .stream().map(HostTemplateEntity::getTemplateId).toList();
                Map<String, Object> m = new HashMap<>();
                m.put("id",          h.getId());
                m.put("hostname",    h.getHostname());
                m.put("ipAddress",   h.getIpAddress()  != null ? h.getIpAddress()  : "");
                m.put("os",          h.getOs()         != null ? h.getOs()         : "");
                m.put("agentActive", h.isAgentActive());
                m.put("templateIds", tplIds);
                return m;
            })
            .toList();
        return ResponseEntity.ok(result);
    }

    // 특정 호스트에 연결된 템플릿 목록 조회
    @GetMapping("/host/{hostId}/linked")
    public ResponseEntity<?> linkedTemplates(@PathVariable("hostId") Long hostId) {
        List<Long> tplIds = hostTemplateRepository.findByHostId(hostId)
            .stream().map(HostTemplateEntity::getTemplateId).toList();
        List<TemplateEntity> templates = templateRepository.findAllById(tplIds);
        return ResponseEntity.ok(templates);
    }

    // 템플릿을 호스트에 적용 — mode에 따라 동작이 다름
    // link: 없는 아이템/트리거만 추가, replace: 기존 것도 덮어씀, unlink: 매핑만 제거, clear: 매핑+복사본 모두 삭제
    @Transactional
    @PostMapping("/{id}/apply")
    public ResponseEntity<?> applyToHosts(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));

        TemplateEntity tpl = templateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();

        String mode = body.containsKey("mode") ? (String) body.get("mode") : "link";
        List<Long> hostIds = ((List<?>) body.get("hostIds")).stream()
            .map(o -> ((Number) o).longValue()).toList();

        // 템플릿의 원본 아이템/트리거만 가져옴 (호스트 복사본 제외)
        List<ItemEntity> tplItems = itemRepository.findByTemplateIdOrderByCreatedAtAsc(id).stream()
            .filter(i -> i.getHostId() == null).toList();
        List<TriggerEntity> tplTriggers = triggerRepository.findByTemplateIdOrderByCreatedAtAsc(id).stream()
            .filter(t -> t.getHostId() == null).toList();

        for (Long hostId : hostIds) {
            if (hostRepository.findById(hostId).isEmpty()) continue;
            if (!isHostAllowed(hostId)) continue; // 권한 없는 호스트 스킵

            switch (mode) {
                case "link" -> {
                    // 이미 연결된 호스트면 스킵
                    if (hostTemplateRepository.existsByHostIdAndTemplateId(hostId, id)) continue;
                    List<ItemEntity> hostItems = itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
                    // 없는 아이템만 추가
                    for (ItemEntity tplItem : tplItems) {
                        boolean exists = hostItems.stream()
                            .anyMatch(i -> i.getItemKey().equals(tplItem.getItemKey()));
                        if (!exists) itemRepository.save(copyItemForHost(tplItem, hostId, id));
                    }
                    List<ItemEntity> hostItemsAfter = itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
                    applyTriggersToHost(tplTriggers, hostId, hostItemsAfter, id, "link");
                    // 호스트-템플릿 매핑 저장
                    HostTemplateEntity link = new HostTemplateEntity();
                    link.setHostId(hostId); link.setTemplateId(id);
                    hostTemplateRepository.save(link);
                }
                case "replace" -> {
                    List<ItemEntity> hostItems = itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
                    for (ItemEntity tplItem : tplItems) {
                        Optional<ItemEntity> existing = hostItems.stream()
                            .filter(i -> i.getItemKey().equals(tplItem.getItemKey())).findFirst();
                        if (existing.isPresent()) {
                            // 기존 아이템을 템플릿 값으로 덮어씀
                            ItemEntity e = existing.get();
                            e.setName(tplItem.getName()); e.setMetric(tplItem.getMetric());
                            e.setValueType(tplItem.getValueType()); e.setUnit(tplItem.getUnit());
                            e.setUnitDisplay(tplItem.getUnitDisplay());
                            e.setDescription(tplItem.getDescription()); e.setEnabled(tplItem.isEnabled());
                            e.setSourceTemplateId(id);
                            itemRepository.save(e);
                        } else {
                            itemRepository.save(copyItemForHost(tplItem, hostId, id));
                        }
                    }
                    List<ItemEntity> hostItemsAfter = itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
                    applyTriggersToHost(tplTriggers, hostId, hostItemsAfter, id, "replace");
                    if (!hostTemplateRepository.existsByHostIdAndTemplateId(hostId, id)) {
                        HostTemplateEntity link = new HostTemplateEntity();
                        link.setHostId(hostId); link.setTemplateId(id);
                        hostTemplateRepository.save(link);
                    }
                }
                case "unlink" -> hostTemplateRepository.deleteByHostIdAndTemplateId(hostId, id);
                case "clear" -> {
                    // 매핑 제거 + 이 템플릿에서 복사된 아이템/트리거까지 삭제
                    List<String> tplItemKeys     = tplItems.stream().map(ItemEntity::getItemKey).toList();
                    List<String> tplTriggerNames = tplTriggers.stream().map(TriggerEntity::getName).toList();
                    List<ItemEntity> hostItems   = itemRepository.findByHostIdOrderByCreatedAtAsc(hostId);
                    List<Long> matchedItemIds    = hostItems.stream()
                        .filter(i -> id.equals(i.getSourceTemplateId()) || tplItemKeys.contains(i.getItemKey()))
                        .map(ItemEntity::getId).toList();
                    triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId).stream()
                        .filter(t -> id.equals(t.getSourceTemplateId())
                                  || tplTriggerNames.contains(t.getName())
                                  || matchedItemIds.contains(t.getItemId()))
                        .map(TriggerEntity::getId).toList()
                        .forEach(tid -> { problemRepository.deleteByTriggerId(tid); triggerRepository.deleteById(tid); });
                    matchedItemIds.forEach(itemRepository::deleteById);
                    hostTemplateRepository.deleteByHostIdAndTemplateId(hostId, id);
                }
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 아이템을 호스트용으로 복사 (sourceTemplateId로 출처 기록)
    private ItemEntity copyItemForHost(ItemEntity src, Long hostId, Long sourceTemplateId) {
        ItemEntity n = new ItemEntity();
        n.setName(src.getName()); n.setItemKey(src.getItemKey()); n.setMetric(src.getMetric());
        n.setValueType(src.getValueType()); n.setUnit(src.getUnit()); n.setUnitDisplay(src.getUnitDisplay());
        n.setDescription(src.getDescription()); n.setEnabled(src.isEnabled());
        n.setHostId(hostId); n.setTemplateId(null); n.setSourceTemplateId(sourceTemplateId);
        return n;
    }

    // 트리거를 호스트에 적용 — link모드: 없는 것만 추가, replace모드: 있으면 덮어씀
    private void applyTriggersToHost(List<TriggerEntity> tplTriggers, Long hostId,
                                     List<ItemEntity> currentHostItems,
                                     Long sourceTemplateId, String mode) {
        List<TriggerEntity> hostTriggers = triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId);
        for (TriggerEntity tplTrigger : tplTriggers) {
            ItemEntity srcItem = itemRepository.findById(tplTrigger.getItemId()).orElse(null);
            if (srcItem == null) continue;
            // 호스트에서 같은 itemKey를 가진 아이템 찾기 (트리거는 아이템과 연결됨)
            Long targetItemId = currentHostItems.stream()
                .filter(i -> i.getItemKey().equals(srcItem.getItemKey()))
                .map(ItemEntity::getId).findFirst().orElse(null);
            if (targetItemId == null) continue;
            Optional<TriggerEntity> existing = hostTriggers.stream()
                .filter(t -> t.getName().equals(tplTrigger.getName())).findFirst();
            if ("link".equals(mode)) {
                if (existing.isEmpty())
                    triggerRepository.save(copyTriggerForHost(tplTrigger, hostId, targetItemId, sourceTemplateId));
            } else {
                if (existing.isPresent()) {
                    TriggerEntity e = existing.get();
                    e.setItemId(targetItemId); e.setFunc(tplTrigger.getFunc());
                    e.setOperator(tplTrigger.getOperator()); e.setThreshold(tplTrigger.getThreshold());
                    e.setExpression(tplTrigger.getExpression()); e.setSeverity(tplTrigger.getSeverity());
                    e.setEnabled(tplTrigger.isEnabled()); e.setDescription(tplTrigger.getDescription());
                    e.setSourceTemplateId(sourceTemplateId);
                    triggerRepository.save(e);
                } else {
                    triggerRepository.save(copyTriggerForHost(tplTrigger, hostId, targetItemId, sourceTemplateId));
                }
            }
        }
    }

    private TriggerEntity copyTriggerForHost(TriggerEntity src, Long hostId,
                                             Long newItemId, Long sourceTemplateId) {
        TriggerEntity n = new TriggerEntity();
        n.setName(src.getName()); n.setItemId(newItemId); n.setHostId(hostId);
        n.setTemplateId(null); n.setSourceTemplateId(sourceTemplateId);
        n.setFunc(src.getFunc()); n.setOperator(src.getOperator()); n.setThreshold(src.getThreshold());
        n.setExpression(src.getExpression()); n.setSeverity(src.getSeverity());
        n.setEnabled(src.isEnabled()); n.setDescription(src.getDescription());
        return n;
    }
}