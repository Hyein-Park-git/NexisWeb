package com.nexis.web.controller;

import com.nexis.web.entity.HostGroupEntity;
import com.nexis.web.entity.HostGroupMemberEntity;
import com.nexis.web.entity.TemplateEntity;
import com.nexis.web.repository.HostGroupMemberRepository;
import com.nexis.web.repository.HostGroupRepository;
import com.nexis.web.repository.HostRepository;
import com.nexis.web.repository.TemplateRepository;
import com.nexis.web.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// @Controller: 뷰(HTML)를 반환하는 컨트롤러. @RestController와 달리 return값이 템플릿 이름
@Controller
public class ConfigurationController {

    private final TemplateRepository        templateRepository;
    private final HostGroupRepository       hostGroupRepository;
    private final HostGroupMemberRepository memberRepository;
    private final HostRepository            hostRepository;
    private final PermissionService         permissionService;

    public ConfigurationController(TemplateRepository        templateRepository,
                                   HostGroupRepository       hostGroupRepository,
                                   HostGroupMemberRepository memberRepository,
                                   HostRepository            hostRepository,
                                   PermissionService         permissionService) {
        this.templateRepository  = templateRepository;
        this.hostGroupRepository = hostGroupRepository;
        this.memberRepository    = memberRepository;
        this.hostRepository      = hostRepository;
        this.permissionService   = permissionService;
    }

    // 템플릿 목록 페이지
    @GetMapping("/configuration/templates")
    public String templates(Model model) {
        model.addAttribute("templates",  templateRepository.findAllByOrderByNameAsc());
        model.addAttribute("canWrite",   permissionService.canWrite("configuration")); // 쓰기 권한 여부를 뷰에 전달
        model.addAttribute("pageTitle",  "Templates");
        model.addAttribute("viewName",   "configuration/templates");
        model.addAttribute("activeMenu", "templates");
        return "layout/main";
    }

    // 호스트 그룹 목록 페이지 — 각 그룹의 멤버 수도 함께 계산
    @GetMapping("/configuration/host-groups")
    public String hostGroups(Model model) {
        List<HostGroupEntity> groups = hostGroupRepository.findAllByOrderByNameAsc();
        Map<Long, Long> memberCountMap = new HashMap<>();
        for (HostGroupEntity g : groups) {
            try {
                memberCountMap.put(g.getId(), (long) memberRepository.findByGroupId(g.getId()).size());
            } catch (Exception e) {
                memberCountMap.put(g.getId(), 0L); // 조회 실패 시 0으로 처리
            }
        }
        model.addAttribute("hostGroups",     groups);
        model.addAttribute("memberCountMap", memberCountMap);
        model.addAttribute("canWrite",       permissionService.canWrite("configuration"));
        model.addAttribute("pageTitle",      "Host Groups");
        model.addAttribute("viewName",       "configuration/host-groups");
        model.addAttribute("activeMenu",     "hostgroups");
        return "layout/main";
    }

    // 호스트 전체 목록 API — 호스트 그룹 멤버 추가 모달에서 사용
    @GetMapping("/api/host-groups-hosts")
    @ResponseBody
    public ResponseEntity<?> availableHosts() {
        try {
            List<Map<String, Object>> result = hostRepository.findAllByOrderByHostnameAsc().stream()
                .map(h -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",          h.getId());
                    m.put("hostname",    h.getHostname());
                    m.put("ipAddress",   h.getIpAddress() != null ? h.getIpAddress() : "");
                    m.put("agentActive", Boolean.TRUE.equals(h.isAgentActive()));
                    return m;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 그룹 추가 API
    @PostMapping("/api/host-groups/add")
    @ResponseBody
    public ResponseEntity<?> addHostGroup(@RequestBody Map<String, String> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            String name = body.get("name");
            if (name == null || name.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Group name is required."));
            if (hostGroupRepository.existsByName(name.trim()))
                return ResponseEntity.badRequest().body(Map.of("error", "Group name already exists."));
            HostGroupEntity g = new HostGroupEntity();
            g.setName(name.trim());
            g.setDescription(body.getOrDefault("description", ""));
            hostGroupRepository.save(g);
            return ResponseEntity.ok(Map.of("success", true, "id", g.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 그룹 수정 API — 이름 중복 체크 포함
    @PutMapping("/api/host-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> updateHostGroup(@PathVariable("id") Long id,
                                             @RequestBody Map<String, String> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            return hostGroupRepository.findById(id).map(g -> {
                if (body.containsKey("name") && !body.get("name").isBlank()) {
                    String newName = body.get("name").trim();
                    // 이름이 바뀌는 경우에만 중복 체크
                    if (!newName.equals(g.getName()) && hostGroupRepository.existsByName(newName))
                        return ResponseEntity.badRequest().body(Map.<String,Object>of("error", "Group name already exists."));
                    g.setName(newName);
                }
                if (body.containsKey("description"))
                    g.setDescription(body.get("description"));
                hostGroupRepository.save(g);
                return ResponseEntity.ok(Map.<String,Object>of("success", true));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 그룹 삭제 API — 멤버도 함께 삭제 (@Transactional: 둘 다 성공하거나 둘 다 롤백)
    @Transactional
    @DeleteMapping("/api/host-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteHostGroup(@PathVariable("id") Long id) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            if (!hostGroupRepository.existsById(id)) return ResponseEntity.notFound().build();
            memberRepository.deleteByGroupId(id);   // 멤버 먼저 삭제
            hostGroupRepository.deleteById(id);      // 그룹 삭제
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 그룹 멤버 목록 조회 — hostId로 호스트 정보를 join해서 반환
    @GetMapping("/api/host-groups/{id}/members")
    @ResponseBody
    public ResponseEntity<?> getMembers(@PathVariable("id") Long id) {
        try {
            List<Map<String, Object>> members = memberRepository.findByGroupId(id).stream()
                .map(m -> {
                    var host = hostRepository.findById(m.getHostId()).orElse(null);
                    Map<String, Object> map = new HashMap<>();
                    map.put("id",          m.getId());
                    map.put("hostname",    host != null ? host.getHostname() : "—");
                    map.put("ipAddress",   host != null && host.getIpAddress() != null ? host.getIpAddress() : "—");
                    map.put("os",          host != null && host.getOs() != null ? host.getOs() : "—");
                    map.put("agentActive", host != null && Boolean.TRUE.equals(host.isAgentActive()));
                    return map;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 그룹에 멤버(호스트) 추가 — 여러 개 한 번에 추가 가능, 이미 있는 건 스킵
    @PostMapping("/api/host-groups/{id}/members")
    @ResponseBody
    public ResponseEntity<?> addMember(@PathVariable("id") Long id,
                                       @RequestBody Map<String, Object> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            if (!hostGroupRepository.existsById(id)) return ResponseEntity.notFound().build();
            List<?> hostIds = (List<?>) body.get("hostIds");
            if (hostIds == null || hostIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "hostIds is required."));
            int added = 0;
            for (Object hid : hostIds) {
                Long hostId = ((Number) hid).longValue();
                if (!hostRepository.existsById(hostId)) continue;          // 존재하지 않는 호스트 스킵
                if (memberRepository.existsByGroupIdAndHostId(id, hostId)) continue; // 이미 멤버면 스킵
                HostGroupMemberEntity m = new HostGroupMemberEntity();
                m.setGroupId(id);
                m.setHostId(hostId);
                memberRepository.save(m);
                added++;
            }
            return ResponseEntity.ok(Map.of("success", true, "added", added));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 그룹에서 멤버 단건 제거
    @Transactional
    @DeleteMapping("/api/host-groups/{id}/members/{memberId}")
    @ResponseBody
    public ResponseEntity<?> removeMember(@PathVariable("id") Long id,
                                          @PathVariable("memberId") Long memberId) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            memberRepository.deleteById(memberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 모든 호스트 그룹 목록 + 멤버 수 조회 (유저 그룹 권한 설정 모달 등에서 사용)
    @GetMapping("/api/host-groups/available-groups")
    @ResponseBody
    public ResponseEntity<?> availableGroups() {
        try {
            List<Map<String, Object>> result = hostGroupRepository.findAllByOrderByNameAsc().stream()
                .map(g -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",          g.getId());
                    m.put("name",        g.getName());
                    m.put("description", g.getDescription() != null ? g.getDescription() : "");
                    m.put("memberCount", memberRepository.findByGroupId(g.getId()).size());
                    return m;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 특정 호스트가 속한 그룹 목록 조회 (호스트 상세 페이지에서 사용)
    @GetMapping("/api/hosts/{hostId}/groups")
    @ResponseBody
    public ResponseEntity<?> getHostGroups(@PathVariable("hostId") Long hostId) {
        try {
            List<Map<String, Object>> result = memberRepository.findByHostId(hostId).stream()
                .map(m -> {
                    var group = hostGroupRepository.findById(m.getGroupId()).orElse(null);
                    Map<String, Object> map = new HashMap<>();
                    map.put("memberId",    m.getId());
                    map.put("groupId",     m.getGroupId());
                    map.put("groupName",   group != null ? group.getName() : "—");
                    map.put("description", group != null && group.getDescription() != null ? group.getDescription() : "");
                    return map;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트를 특정 그룹에서 제거 (호스트 상세 → 그룹 탭에서 사용)
    @Transactional
    @DeleteMapping("/api/hosts/{hostId}/groups/{memberId}")
    @ResponseBody
    public ResponseEntity<?> removeHostFromGroup(@PathVariable("hostId") Long hostId,
                                                 @PathVariable("memberId") Long memberId) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            memberRepository.deleteById(memberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트를 여러 그룹에 한 번에 추가
    @PostMapping("/api/hosts/{hostId}/groups")
    @ResponseBody
    public ResponseEntity<?> addHostToGroups(@PathVariable("hostId") Long hostId,
                                             @RequestBody Map<String, Object> body) {
        if (!permissionService.canWrite("configuration"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            if (!hostRepository.existsById(hostId))
                return ResponseEntity.notFound().build();
            List<?> groupIds = (List<?>) body.get("groupIds");
            if (groupIds == null || groupIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "groupIds is required."));
            int added = 0;
            for (Object gid : groupIds) {
                Long groupId = ((Number) gid).longValue();
                if (!hostGroupRepository.existsById(groupId)) continue;
                if (memberRepository.existsByGroupIdAndHostId(groupId, hostId)) continue;
                HostGroupMemberEntity m = new HostGroupMemberEntity();
                m.setGroupId(groupId);
                m.setHostId(hostId);
                memberRepository.save(m);
                added++;
            }
            return ResponseEntity.ok(Map.of("success", true, "added", added));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 템플릿 상세 페이지
    @GetMapping("/configuration/templates/{id}")
    public String templateDetail(@PathVariable("id") Long id, Model model) {
        TemplateEntity template = templateRepository.findById(id).orElse(null);
        if (template == null) return "redirect:/configuration/templates"; // 없으면 목록으로
        model.addAttribute("template",  template);
        model.addAttribute("canWrite",  permissionService.canWrite("configuration"));
        model.addAttribute("pageTitle", template.getName());
        model.addAttribute("viewName",  "configuration/template-detail");
        model.addAttribute("activeMenu","templates");
        return "layout/main";
    }
}