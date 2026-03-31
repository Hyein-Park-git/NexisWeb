package com.nexis.web.controller;

import com.nexis.web.model.InstallationConfig;
import com.nexis.web.service.InstallationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

// @RestController: @Controller + @ResponseBody. 모든 메서드가 JSON을 반환
@RestController
@RequestMapping("/api")
public class InstallApiController {

    private final InstallationService installationService;

    public InstallApiController(InstallationService installationService) {
        this.installationService = installationService;
    }

    // 설치 마법사 3단계 — DB 연결 테스트 (저장 전에 먼저 확인)
    @PostMapping("/install/test-db")
    public ResponseEntity<Map<String, Object>> testDb(@RequestBody InstallationConfig config) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = installationService.checkDbConnection(config);
            result.put("success", success);
            result.put("message", success ? "Connection successful." : "Failed to connect to database.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // 설치 마법사 4단계 — Nexis 서버 연결 테스트
    @GetMapping("/install/test-server")
    public ResponseEntity<Map<String, Object>> testServer(
            @RequestParam("host") String host,
            @RequestParam("port") int port) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = installationService.checkServerConnection(host, port);
            result.put("success", success);
            result.put("message", success
                ? "Connected to " + host + ":" + port + " successfully."
                : "Cannot connect to " + host + ":" + port + ". Check that the server is running.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // 헤더/레이아웃에서 서버 이름·호스트·포트를 표시하기 위한 API
    @GetMapping("/server-info")
    public ResponseEntity<Map<String, Object>> serverInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            result.put("serverName", config.getServerName() != null ? config.getServerName() : "-");
            result.put("serverHost", config.getServerHost() != null ? config.getServerHost() : "-");
            result.put("serverPort", config.getServerPort() > 0 ? String.valueOf(config.getServerPort()) : "-");
        } catch (Exception e) {
            result.put("serverName", "-");
            result.put("serverHost", "-");
            result.put("serverPort", "-");
        }
        return ResponseEntity.ok(result);
    }

    // configuration/settings 페이지에서 현재 DB 설정을 표시하기 위한 API (비밀번호는 제외)
    @GetMapping("/config-info")
    public ResponseEntity<Map<String, Object>> configInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            result.put("dbType", config.getDbType() != null ? config.getDbType() : "-");
            result.put("dbHost", config.getDbHost() != null ? config.getDbHost() : "-");
            result.put("dbPort", config.getDbPort() > 0 ? config.getDbPort() : "-");
            result.put("dbName", config.getDbName() != null ? config.getDbName() : "-");
            result.put("dbUser", config.getDbUser() != null ? config.getDbUser() : "-");
        } catch (Exception e) {
            result.put("dbType", "-");
            result.put("dbHost", "-");
            result.put("dbPort", "-");
            result.put("dbName", "-");
        }
        return ResponseEntity.ok(result);
    }

    // 서버 설정 저장 (serverName, serverHost, serverPort)
    @PostMapping("/settings/server")
    public ResponseEntity<Map<String, Object>> saveServerSettings(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            if (body.containsKey("serverName")) config.setServerName((String) body.get("serverName"));
            if (body.containsKey("serverHost")) config.setServerHost((String) body.get("serverHost"));
            if (body.containsKey("serverPort")) {
                Object p = body.get("serverPort");
                // JSON 숫자는 Integer로 올 수도 있고 String으로 올 수도 있어서 분기 처리
                config.setServerPort(p instanceof Number ? ((Number) p).intValue() : Integer.parseInt(p.toString()));
            }
            installationService.saveConfig(config);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // DB 설정 저장 — 비밀번호는 입력된 경우에만 업데이트 (빈값이면 기존 유지)
    @PostMapping("/settings/db")
    public ResponseEntity<Map<String, Object>> saveDbSettings(@RequestBody InstallationConfig incoming) {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            if (incoming.getDbType() != null)     config.setDbType(incoming.getDbType());
            if (incoming.getDbHost() != null)     config.setDbHost(incoming.getDbHost());
            if (incoming.getDbPort() > 0)         config.setDbPort(incoming.getDbPort());
            if (incoming.getDbName() != null)     config.setDbName(incoming.getDbName());
            if (incoming.getDbUser() != null)     config.setDbUser(incoming.getDbUser());
            if (incoming.getDbPassword() != null && !incoming.getDbPassword().isBlank())
                config.setDbPassword(incoming.getDbPassword());
            installationService.saveConfig(config);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // 현재 저장된 DB 설정으로 연결 테스트 (설정 페이지의 "Test Connection" 버튼)
    @GetMapping("/settings/test-db-current")
    public ResponseEntity<Map<String, Object>> testDbCurrent() {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            boolean success = installationService.checkDbConnection(config);
            result.put("success", success);
            result.put("message", success ? "Connected" : "Unreachable");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // 현재 저장된 서버 설정으로 연결 테스트
    @GetMapping("/settings/test-server-current")
    public ResponseEntity<Map<String, Object>> testServerCurrent() {
        Map<String, Object> result = new HashMap<>();
        try {
            InstallationConfig config = installationService.loadConfig();
            boolean success = installationService.checkServerConnection(
                config.getServerHost(), config.getServerPort());
            result.put("success", success);
            result.put("message", success ? "Connected" : "Unreachable");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}