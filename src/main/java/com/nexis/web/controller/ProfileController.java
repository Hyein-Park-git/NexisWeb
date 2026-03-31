package com.nexis.web.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {

    // 프로필 페이지 — Authentication 객체에서 로그인 유저명 추출
    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        model.addAttribute("pageTitle",  "Profile");
        model.addAttribute("viewName",   "user/profile");
        model.addAttribute("activeMenu", "");
        model.addAttribute("username",   auth != null ? auth.getName() : "Admin");
        return "layout/main";
    }

    // 개인 설정 페이지 (비밀번호 변경 등)
    @GetMapping("/settings")
    public String userSettings(Model model) {
        model.addAttribute("pageTitle",  "Settings");
        model.addAttribute("viewName",   "user/settings");
        model.addAttribute("activeMenu", "");
        return "layout/main";
    }

    // 시스템 설정 페이지 — Administrator 롤만 접근 가능
    // Spring Security는 로그인 여부만 체크하므로, 여기서 직접 롤을 확인해 403 처리
    @GetMapping("/configuration/settings")
    public String configSettings(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("Administrator"));
        if (!isAdmin) {
            throw new AccessDeniedException("Administrator only"); // SecurityConfig의 accessDeniedHandler가 처리
        }
        model.addAttribute("pageTitle",  "System Settings");
        model.addAttribute("viewName",   "administration/settings");
        model.addAttribute("activeMenu", "config-settings");
        return "layout/main";
    }
}