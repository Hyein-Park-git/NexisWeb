package com.nexis.web.controller;

import com.nexis.web.entity.HostEntity;
import com.nexis.web.repository.HostRepository;
import com.nexis.web.service.HostService;
import com.nexis.web.service.ItemService;
import com.nexis.web.service.PermissionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/monitoring")
public class LatestDataController {

    private final HostRepository    hostRepository;
    private final HostService       hostService;
    private final ItemService       itemService;
    private final PermissionService permissionService;

    public LatestDataController(HostRepository    hostRepository,
                                HostService       hostService,
                                ItemService       itemService,
                                PermissionService permissionService) {
        this.hostRepository    = hostRepository;
        this.hostService       = hostService;
        this.itemService       = itemService;
        this.permissionService = permissionService;
    }

    // Latest Data 목록 페이지 — 허용된 호스트 목록을 넘겨줌
    @GetMapping("/latest-data")
    public String latestData(Model model) {
        model.addAttribute("metrics",    hostService.getAllowedHosts());
        model.addAttribute("pageTitle",  "Latest Data");
        model.addAttribute("viewName",   "monitoring/latest-data");
        model.addAttribute("activeMenu", "latest");
        return "layout/main";
    }

    // 특정 호스트의 최신 데이터 상세 페이지 — 허용되지 않은 호스트면 목록으로 리다이렉트
    @GetMapping("/latest-data/{hostId}")
    public String dataDetail(@PathVariable("hostId") Long hostId, Model model) {
        List<HostEntity> allowed = hostService.getAllowedHosts();
        boolean isAllowed = allowed.stream().anyMatch(h -> h.getId().equals(hostId));
        if (!isAllowed) return "redirect:/monitoring/latest-data";

        HostEntity host = hostRepository.findById(hostId).orElse(null);
        if (host == null) return "redirect:/monitoring/latest-data";

        model.addAttribute("host",       host);
        model.addAttribute("hostname",   host.getHostname());
        model.addAttribute("pageTitle",  host.getHostname() + " — Latest Data");
        model.addAttribute("viewName",   "monitoring/data-detail");
        model.addAttribute("activeMenu", "latest");
        return "layout/main";
    }
}