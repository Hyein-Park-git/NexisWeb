package com.nexis.web.controller;

import com.nexis.web.entity.HostEntity;
import com.nexis.web.entity.ProblemEntity;
import com.nexis.web.repository.ProblemRepository;
import com.nexis.web.service.HostService;
import com.nexis.web.service.PermissionService;
import com.nexis.web.service.TriggerService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ProblemsController {

    private final TriggerService    triggerService;
    private final ProblemRepository problemRepository;
    private final HostService       hostService;
    private final PermissionService permissionService;

    public ProblemsController(TriggerService    triggerService,
                              ProblemRepository problemRepository,
                              HostService       hostService,
                              PermissionService permissionService) {
        this.triggerService    = triggerService;
        this.problemRepository = problemRepository;
        this.hostService       = hostService;
        this.permissionService = permissionService;
    }

    // 문제(Problem) 목록 페이지 — 다양한 필터 파라미터를 받아 조건부 조회
    @GetMapping("/monitoring/problems")
    public String problems(
            @RequestParam(name = "show",     defaultValue = "problems") String show,     // problems/recent/history
            @RequestParam(name = "severity", required = false)          List<String> severities,
            @RequestParam(name = "host",     defaultValue = "")         String host,
            @RequestParam(name = "trigger",  defaultValue = "")         String trigger,
            @RequestParam(name = "problem",  defaultValue = "")         String problem,
            @RequestParam(name = "ageLess",  defaultValue = "")         String ageLess,  // N일 이내 필터
            Model model) {

        // 허용된 호스트 hostname Set (권한 필터링용)
        Set<String> allowedHostnames = hostService.getAllowedHosts().stream()
            .map(HostEntity::getHostname)
            .collect(Collectors.toSet());

        // show 파라미터에 따라 다른 데이터 조회
        List<ProblemEntity> all;
        if ("history".equals(show)) {
            all = triggerService.getAllProblems();      // 전체 이력
        } else if ("recent".equals(show)) {
            all = triggerService.getRecentProblems();  // 최근 발생
        } else {
            all = triggerService.getActiveProblems();  // 현재 활성
        }

        // 허용된 호스트만 필터링
        all = all.stream()
            .filter(p -> p.getHostname() != null && allowedHostnames.contains(p.getHostname()))
            .collect(Collectors.toList());

        // ageLess: N일 이내 발생한 것만 필터
        if (!ageLess.isBlank()) {
            try {
                int days = Integer.parseInt(ageLess.trim());
                LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
                all = all.stream()
                    .filter(p -> p.getStartedAt() != null && p.getStartedAt().isAfter(cutoff))
                    .collect(Collectors.toList());
            } catch (NumberFormatException ignored) {}
        }

        // severity 체크박스 필터
        if (severities != null && !severities.isEmpty()) {
            all = all.stream()
                .filter(p -> severities.contains(p.getSeverity()))
                .collect(Collectors.toList());
        }

        // 호스트명 부분 검색 (대소문자 무시)
        if (!host.isBlank()) {
            String lc = host.toLowerCase();
            all = all.stream()
                .filter(p -> p.getHostname() != null
                          && p.getHostname().toLowerCase().contains(lc))
                .collect(Collectors.toList());
        }

        // 트리거 이름 또는 expression 부분 검색
        if (!trigger.isBlank()) {
            String lc = trigger.toLowerCase();
            all = all.stream()
                .filter(p ->
                    (p.getTriggerName() != null && p.getTriggerName().toLowerCase().contains(lc))
                 || (p.getExpression()  != null && p.getExpression().toLowerCase().contains(lc))
                )
                .collect(Collectors.toList());
        }

        // 문제(트리거명 또는 아이템명) 부분 검색
        if (!problem.isBlank()) {
            String lc = problem.toLowerCase();
            all = all.stream()
                .filter(p ->
                    (p.getTriggerName() != null && p.getTriggerName().toLowerCase().contains(lc))
                 || (p.getItemName()    != null && p.getItemName().toLowerCase().contains(lc))
                )
                .collect(Collectors.toList());
        }

        model.addAttribute("problems",   all);
        model.addAttribute("show",       show);
        model.addAttribute("severities", severities != null ? severities : List.of());
        model.addAttribute("host",       host);
        model.addAttribute("trigger",    trigger);
        model.addAttribute("problem",    problem);
        model.addAttribute("ageLess",    ageLess);
        model.addAttribute("pageTitle",  "Problems");
        model.addAttribute("viewName",   "monitoring/problems");
        model.addAttribute("activeMenu", "problems");
        return "layout/main";
    }

    // 문제 Acknowledge 토글 API (PROBLEM ↔ ACKNOWLEDGED)
    @PutMapping("/api/problems/{id}/acknowledge")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> acknowledge(
            @PathVariable(name = "id") Long id) {
        return problemRepository.findById(id)
            .map(p -> {
                // 이미 ACKNOWLEDGED면 다시 PROBLEM으로 되돌림
                String next = "ACKNOWLEDGED".equals(p.getStatus()) ? "PROBLEM" : "ACKNOWLEDGED";
                p.setStatus(next);
                problemRepository.save(p);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "status", next));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // 여러 문제를 한 번에 상태 변경하는 API (일괄 처리)
    @PostMapping("/api/problems/mass-update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> massUpdate(@RequestBody MassUpdateRequest req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "No IDs provided"));
        }

        String newStatus = req.getStatus();
        if (!List.of("PROBLEM", "ACKNOWLEDGED", "RESOLVED").contains(newStatus)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Invalid status: " + newStatus));
        }

        List<ProblemEntity> targets = problemRepository.findAllById(req.getIds());
        targets.forEach(p -> {
            p.setStatus(newStatus);
            // RESOLVED로 바꿀 때 resolvedAt 기록 (이미 있으면 건드리지 않음)
            if ("RESOLVED".equals(newStatus) && p.getResolvedAt() == null) {
                p.setResolvedAt(LocalDateTime.now());
            }
            // RESOLVED가 아닌 상태로 변경 시 resolvedAt 초기화
            if (!"RESOLVED".equals(newStatus)) {
                p.setResolvedAt(null);
            }
        });
        problemRepository.saveAll(targets); // 한 번에 저장 (saveAll = 배치)

        return ResponseEntity.ok(Map.<String, Object>of("success", true, "updated", targets.size()));
    }

    // mass-update 요청 body를 받기 위한 내부 DTO 클래스
    public static class MassUpdateRequest {
        private List<Long> ids;
        private String status;
        public List<Long> getIds()       { return ids; }
        public void setIds(List<Long> v) { this.ids = v; }
        public String getStatus()        { return status; }
        public void setStatus(String v)  { this.status = v; }
    }
}