package com.nexis.web.controller;

import com.nexis.web.entity.HostGroupEntity;
import com.nexis.web.entity.UserEntity;
import com.nexis.web.model.InstallationConfig;
import com.nexis.web.repository.HostGroupRepository;
import com.nexis.web.repository.UserRepository;
import com.nexis.web.service.InstallationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 설치 마법사 컨트롤러 — 단계별 페이지(GET)와 저장(POST)을 처리
@Controller
@RequestMapping("/install")
public class InstallController {

    private final InstallationService installationService;
    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final HostGroupRepository hostGroupRepository;

    public InstallController(InstallationService installationService,
                             UserRepository      userRepository,
                             PasswordEncoder     passwordEncoder,
                             HostGroupRepository hostGroupRepository) {
        this.installationService = installationService;
        this.userRepository      = userRepository;
        this.passwordEncoder     = passwordEncoder;
        this.hostGroupRepository = hostGroupRepository;
    }

    // /install 진입 시 1단계(welcome)으로 리다이렉트
    @GetMapping
    public String index() {
        return "redirect:/install/welcome";
    }

    // 1단계: Welcome 페이지
    @GetMapping("/welcome")
    public String welcome(Model model) {
        model.addAttribute("step",     1);
        model.addAttribute("stepName", "Welcome");
        model.addAttribute("viewName", "install/welcome");
        return "layout/install";
    }

    // 2단계: 사전 요구사항 체크 (Java 버전, DB 드라이버 등)
    @GetMapping("/prerequisites")
    public String prerequisites(Model model) {
        model.addAttribute("check",    installationService.checkPrerequisites()); // 체크 결과를 뷰로 전달
        model.addAttribute("step",     2);
        model.addAttribute("stepName", "Check of pre-requisites");
        model.addAttribute("viewName", "install/prerequisites");
        return "layout/install";
    }

    // 3단계: DB 연결 설정 폼 페이지
    @GetMapping("/db-connection")
    public String dbConnection(Model model) {
        model.addAttribute("config",   installationService.loadConfig()); // 기존에 입력한 값이 있으면 유지
        model.addAttribute("step",     3);
        model.addAttribute("stepName", "Configure DB connection");
        model.addAttribute("viewName", "install/db-connection");
        return "layout/install";
    }

    // 3단계: DB 설정 저장 후 다음 단계로
    @PostMapping("/db-connection")
    public String dbConnectionPost(@ModelAttribute InstallationConfig config,
                                   RedirectAttributes redirectAttributes) {
        try {
            installationService.saveConfig(config);
            return "redirect:/install/server-details";
        } catch (Exception e) {
            // RedirectAttributes: 리다이렉트 후에도 플래시 메시지를 한 번만 전달
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/install/db-connection";
        }
    }

    // 4단계: Nexis 서버 정보 입력 폼 페이지
    @GetMapping("/server-details")
    public String serverDetails(Model model) {
        model.addAttribute("config",   installationService.loadConfig());
        model.addAttribute("step",     4);
        model.addAttribute("stepName", "Nexis server details");
        model.addAttribute("viewName", "install/server-details");
        return "layout/install";
    }

    // 4단계: 서버 정보 저장 — DB 설정은 건드리지 않고 서버 관련 필드만 업데이트
    @PostMapping("/server-details")
    public String serverDetailsPost(@ModelAttribute InstallationConfig config,
                                    RedirectAttributes redirectAttributes) {
        try {
            InstallationConfig existing = installationService.loadConfig();
            existing.setServerHost(config.getServerHost());
            existing.setServerPort(config.getServerPort());
            existing.setServerName(config.getServerName());
            installationService.saveConfig(existing);
            return "redirect:/install/summary";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/install/server-details";
        }
    }

    // 5단계: 설치 전 요약 확인 페이지
    @GetMapping("/summary")
    public String summary(Model model) {
        model.addAttribute("config",   installationService.loadConfig());
        model.addAttribute("step",     5);
        model.addAttribute("stepName", "Pre-installation summary");
        model.addAttribute("viewName", "install/summary");
        return "layout/install";
    }

    // 설치 완료 처리 — Admin 계정 생성 + Default Group 생성 + installed 플래그 저장
    @PostMapping("/finish")
    public String finish(RedirectAttributes redirectAttributes) {
        InstallationConfig config = installationService.loadConfig();
        try {
            config.setInstalled(true);
            installationService.saveConfig(config);

            // Admin 계정이 없을 때만 생성 (중복 실행 방지)
            if (!userRepository.existsByUsername("Admin")) {
                UserEntity admin = new UserEntity();
                admin.setUsername("Admin");
                admin.setFullName("Administrator");
                admin.setPassword(passwordEncoder.encode("nexis")); // 초기 비밀번호 암호화 저장
                admin.setRole("Administrator");
                admin.setEnabled(true);
                userRepository.save(admin);
            }

            // Default Group이 없을 때만 생성
            if (!hostGroupRepository.existsByName("Default Group")) {
                HostGroupEntity defaultGroup = new HostGroupEntity();
                defaultGroup.setName("Default Group");
                defaultGroup.setDescription("Default host group");
                hostGroupRepository.save(defaultGroup);
            }

            return "redirect:/install/complete";

        } catch (Exception e) {
            // 실패 시 installed 플래그를 false로 되돌려서 재설치 가능하게 함
            config.setInstalled(false);
            try { installationService.saveConfig(config); } catch (Exception ignored) {}
            redirectAttributes.addFlashAttribute("error", "Installation failed: " + e.getMessage());
            return "redirect:/install/summary";
        }
    }

    // 6단계: 설치 완료 페이지 — 설치가 실제로 완료된 경우에만 표시
    @GetMapping("/complete")
    public String complete(Model model) {
        InstallationConfig config = installationService.loadConfig();
        if (config == null || !config.isInstalled()) {
            return "redirect:/install/summary"; // 설치 미완료면 요약으로 되돌림
        }
        model.addAttribute("step",     6);
        model.addAttribute("stepName", "Install Complete");
        model.addAttribute("viewName", "install/complete");
        return "layout/install";
    }
}