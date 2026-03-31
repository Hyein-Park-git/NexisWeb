package com.nexis.web.controller;

import com.nexis.web.entity.HostEntity;
import com.nexis.web.repository.HostGroupMemberRepository;
import com.nexis.web.repository.HostGroupRepository;
import com.nexis.web.service.HostService;
import com.nexis.web.service.PermissionService;
import com.nexis.web.repository.HostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HostsController {

    private final HostService               hostService;
    private final HostRepository            hostRepository;
    private final PermissionService         permissionService;
    private final HostGroupMemberRepository hostGroupMemberRepository;
    private final HostGroupRepository       hostGroupRepository;

    public HostsController(HostService               hostService,
                           HostRepository            hostRepository,
                           PermissionService         permissionService,
                           HostGroupMemberRepository hostGroupMemberRepository,
                           HostGroupRepository       hostGroupRepository) {
        this.hostService               = hostService;
        this.hostRepository            = hostRepository;
        this.permissionService         = permissionService;
        this.hostGroupMemberRepository = hostGroupMemberRepository;
        this.hostGroupRepository       = hostGroupRepository;
    }

    // 현재 유저가 특정 호스트에 접근 가능한지 확인하는 헬퍼 메서드
    private boolean isHostAllowed(Long hostId) {
        List<Long> allowedGroups = permissionService.getAllowedHostGroupIds();
        if (allowedGroups == null) return false;
        if (allowedGroups.isEmpty()) return true; // Administrator는 모든 호스트 허용

        // 이 호스트가 속한 그룹 ID 목록
        List<Long> hostGroupIds = hostGroupMemberRepository.findByHostId(hostId)
            .stream().map(m -> m.getGroupId()).toList();

        // Default Group에 속한 호스트는 항상 허용
        java.util.Optional<Long> defaultGroupId = hostGroupRepository.findByName("Default Group")
            .map(g -> g.getId());
        if (defaultGroupId.isPresent() && hostGroupIds.contains(defaultGroupId.get())) return true;

        // -1L: 소속 UserGroup이 없는 경우 → Default Group만 허용
        if (allowedGroups.contains(-1L)) return false;

        // 허용된 그룹 중 하나라도 포함되면 접근 허용
        return hostGroupIds.stream().anyMatch(allowedGroups::contains);
    }

    // 모니터링 > 호스트 목록 페이지
    @GetMapping("/monitoring/hosts")
    public String monitoringHosts(Model model) {
        model.addAttribute("hosts",      hostService.getAllowedHosts());
        model.addAttribute("canWrite",   permissionService.canWrite("monitoring"));
        model.addAttribute("pageTitle",  "Hosts");
        model.addAttribute("viewName",   "monitoring/hosts");
        model.addAttribute("activeMenu", "hosts");
        return "layout/main";
    }

    // 설정 > 호스트 목록 페이지 (같은 뷰, 다른 권한 체크)
    @GetMapping("/configuration/hosts")
    public String configHosts(Model model) {
        model.addAttribute("hosts",      hostService.getAllowedHosts());
        model.addAttribute("canWrite",   permissionService.canWrite("configuration"));
        model.addAttribute("pageTitle",  "Configuration — Hosts");
        model.addAttribute("viewName",   "monitoring/hosts");
        model.addAttribute("activeMenu", "config-hosts");
        return "layout/main";
    }

    // 호스트 상세 페이지 — 접근 권한 없으면 목록으로 리다이렉트
    @GetMapping("/monitoring/hosts/{id}")
    public String hostDetail(@PathVariable("id") Long id, Model model) {
        if (!isHostAllowed(id)) return "redirect:/monitoring/hosts";
        HostEntity host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/monitoring/hosts";
        model.addAttribute("host",       host);
        model.addAttribute("canWrite",   permissionService.canWrite("monitoring") && isHostAllowed(id));
        model.addAttribute("pageTitle",  host.getHostname() + " — Detail");
        model.addAttribute("viewName",   "monitoring/host-detail");
        model.addAttribute("activeMenu", "hosts");
        return "layout/main";
    }

    // 호스트 추가 API
    @PostMapping("/api/hosts/add")
    @ResponseBody
    public ResponseEntity<?> addHost(@RequestBody Map<String, String> body) {
        if (!permissionService.canWrite("monitoring"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        try {
            String hostname = body.get("hostname");
            if (hostname == null || hostname.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Hostname is required."));
            String ipAddress   = body.getOrDefault("ipAddress", "");
            String description = body.getOrDefault("description", "");
            HostEntity host = hostService.addHost(hostname, ipAddress, description);
            return ResponseEntity.ok(Map.of("success", true, "id", host.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 정보 수정 API (IP, 설명, 활성화 여부)
    @PutMapping("/api/hosts/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateHost(@PathVariable("id") Long id,
                                        @RequestBody Map<String, Object> body) {
        if (!permissionService.canWrite("monitoring"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        if (!isHostAllowed(id))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied for this host."));
        try {
            return hostRepository.findById(id)
                .map(host -> {
                    if (body.containsKey("ipAddress"))
                        host.setIpAddress((String) body.get("ipAddress"));
                    if (body.containsKey("description"))
                        host.setDescription((String) body.get("description"));
                    if (body.containsKey("enabled"))
                        host.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
                    hostRepository.save(host);
                    return ResponseEntity.ok(Map.<String, Object>of("success", true));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 삭제 API
    @DeleteMapping("/api/hosts/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteHost(@PathVariable("id") Long id) {
        if (!permissionService.canWrite("monitoring"))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied."));
        if (!isHostAllowed(id))
            return ResponseEntity.status(403).body(Map.of("error", "Permission denied for this host."));
        try {
            hostService.deleteHost(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 호스트 에이전트 상태 폴링 API (대시보드 실시간 상태 갱신용)
    @GetMapping("/api/hosts/status")
    @ResponseBody
    public ResponseEntity<?> hostStatus() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> result = hostService.getAllowedHosts().stream()
            .map(h -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id",          h.getId());
                m.put("agentActive", h.isAgentActive());
                m.put("lastCheck",   h.getAgentLastCheck() != null
                    ? h.getAgentLastCheck().format(fmt) : null);
                return m;
            })
            .toList();
        return ResponseEntity.ok(result);
    }

    // 개발/디버그용 — 현재 유저의 권한 정보 확인
    @GetMapping("/debug/permission")
    @ResponseBody
    public Object debugPermission() {
        return Map.of(
            "role",          permissionService.getAccessLevel("monitoring"),
            "canWrite",      permissionService.canWrite("monitoring"),
            "canRead",       permissionService.canRead("monitoring"),
            "allowedGroups", permissionService.getAllowedHostGroupIds()
        );
    }
}