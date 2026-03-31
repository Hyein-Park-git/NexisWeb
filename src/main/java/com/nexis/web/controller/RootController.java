package com.nexis.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    // "/" 루트 경로 진입 시 로그인 여부에 따라 분기
    @GetMapping("/")
    public String root() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // auth가 존재하고, 인증된 상태이며, 익명 사용자가 아닌 경우 → 대시보드로
        // "anonymousUser": Spring Security가 미인증 사용자에게 부여하는 기본 principal 문자열
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/monitoring/dashboard";
        }

        // 로그인 안 된 상태 → 로그인 페이지로
        return "redirect:/login";
    }
}