package com.nexis.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    // 홈 페이지 (현재는 사용 여부가 낮지만 확장용으로 존재)
    @GetMapping("/main/home")
    public String home(Model model) {
        model.addAttribute("message", "Welcome to Nexis Main Page!");
        return "home";
    }

    // About 페이지
    @GetMapping("/main/about")
    public String about(Model model) {
        model.addAttribute("info", "About Nexis System");
        return "about";
    }
}