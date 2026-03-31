package com.nexis.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Spring Security가 로그인 실패 시 ?error=true를 붙여서 리다이렉트함
        if ("true".equals(request.getParameter("error"))) {
            model.addAttribute("errorMessage", "Invalid username or password");
        }
        // 로그아웃 성공 시 ?logout=true를 붙여서 리다이렉트함
        if ("true".equals(request.getParameter("logout"))) {
            model.addAttribute("logoutMessage", "You have been logged out successfully.");
        }
        return "login"; // templates/login.html 렌더링
    }
}