package com.nexis.web.controller;

import com.nexis.web.entity.HostEntity;
import com.nexis.web.entity.ProblemEntity;
import com.nexis.web.repository.ItemDataRepository;
import com.nexis.web.service.HostService;
import com.nexis.web.service.PermissionService;
import com.nexis.web.service.TriggerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/monitoring")
public class DashboardController {

    private final ItemDataRepository itemDataRepository;
    private final HostService        hostService;
    private final TriggerService     triggerService;
    private final PermissionService  permissionService;

    public DashboardController(ItemDataRepository itemDataRepository,
                               HostService        hostService,
                               TriggerService     triggerService,
                               PermissionService  permissionService) {
        this.itemDataRepository = itemDataRepository;
        this.hostService        = hostService;
        this.triggerService     = triggerService;
        this.permissionService  = permissionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // 현재 로그인 유저가 접근 가능한 호스트만 조회
        List<HostEntity> allowedHosts = hostService.getAllowedHosts();
        // 빠른 포함 여부 체크를 위해 hostname Set으로 변환
        Set<String> allowedHostnames = allowedHosts.stream()
            .map(HostEntity::getHostname)
            .collect(Collectors.toSet());

        // 호스트 통계
        long totalHosts   = allowedHosts.size();
        long onlineHosts  = allowedHosts.stream().filter(h -> Boolean.TRUE.equals(h.isAgentActive())).count();
        long offlineHosts = totalHosts - onlineHosts;
        long totalMetrics = itemDataRepository.count(); // 전체 수집 데이터 수

        // 활성 Problem 중 허용된 호스트 것만 필터링
        List<ProblemEntity> active = triggerService.getActiveProblems().stream()
            .filter(p -> p.getHostname() != null && allowedHostnames.contains(p.getHostname()))
            .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 뷰에 넘길 problem 리스트 — null 안전하게 "—"으로 치환
        List<Map<String, Object>> problems = active.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hostname",    p.getHostname()    != null ? p.getHostname()    : "—");
            m.put("item",        p.getItemName()    != null ? p.getItemName()    : "—");
            m.put("trigger",     p.getTriggerName() != null ? p.getTriggerName() : "—");
            m.put("value",       p.getValue()       != null ? p.getValue()       : "—");
            m.put("severity",    p.getSeverity()    != null ? p.getSeverity()    : "Not classified");
            m.put("collectedAt", p.getStartedAt()   != null ? p.getStartedAt().format(formatter) : "—");
            return m;
        }).collect(Collectors.toList());

        // severity별 카운트 (대시보드 상단 배지용)
        long cntDisaster      = active.stream().filter(p -> "Disaster".equals(p.getSeverity())).count();
        long cntHigh          = active.stream().filter(p -> "High".equals(p.getSeverity())).count();
        long cntAverage       = active.stream().filter(p -> "Average".equals(p.getSeverity())).count();
        long cntWarning       = active.stream().filter(p -> "Warning".equals(p.getSeverity())).count();
        long cntInformation   = active.stream().filter(p -> "Information".equals(p.getSeverity())).count();
        long cntNotClassified = active.stream().filter(p -> "Not classified".equals(p.getSeverity())).count();

        // 호스트별 severity 카운트 Map 생성 (호스트 문제 요약 테이블용)
        // { hostname → { severity → count } }
        Map<String, Map<String, Integer>> hostSevMap = new LinkedHashMap<>();
        for (ProblemEntity p : active) {
            String hostname = p.getHostname() != null ? p.getHostname() : "—";
            String severity = p.getSeverity() != null ? p.getSeverity() : "Not classified";
            hostSevMap.computeIfAbsent(hostname, k -> new LinkedHashMap<>())
                      .merge(severity, 1, Integer::sum); // 있으면 +1, 없으면 1로 초기화
        }

        // severity 표시 순서 정의
        List<String> sevOrder = Arrays.asList(
            "Disaster", "High", "Average", "Warning", "Information", "Not classified");

        // 호스트별로 severity 배지 리스트를 순서에 맞게 정렬
        Map<String, List<Map<String, Object>>> problemHostMap = new LinkedHashMap<>();
        hostSevMap.forEach((hostname, sevCounts) -> {
            List<Map<String, Object>> badges = new ArrayList<>();
            sevOrder.forEach(sev -> {
                if (sevCounts.containsKey(sev)) {
                    Map<String, Object> badge = new LinkedHashMap<>();
                    badge.put("severity", sev);
                    badge.put("count", sevCounts.get(sev));
                    badges.add(badge);
                }
            });
            problemHostMap.put(hostname, badges);
        });

        model.addAttribute("totalHosts",       totalHosts);
        model.addAttribute("onlineHosts",      onlineHosts);
        model.addAttribute("offlineHosts",     offlineHosts);
        model.addAttribute("totalMetrics",     totalMetrics);
        model.addAttribute("problems",         problems);
        model.addAttribute("problemHostMap",   problemHostMap);
        model.addAttribute("cntDisaster",      cntDisaster);
        model.addAttribute("cntHigh",          cntHigh);
        model.addAttribute("cntAverage",       cntAverage);
        model.addAttribute("cntWarning",       cntWarning);
        model.addAttribute("cntInformation",   cntInformation);
        model.addAttribute("cntNotClassified", cntNotClassified);
        model.addAttribute("pageTitle",        "Dashboard");
        model.addAttribute("viewName",         "monitoring/dashboard");
        model.addAttribute("activeMenu",       "dashboard");
        return "layout/main";
    }
}