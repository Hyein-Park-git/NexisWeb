package com.nexis.web.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// @Data: getter/setter/toString/equals/hashCode 자동 생성 (Lombok)
// @NoArgsConstructor: 파라미터 없는 기본 생성자 자동 생성
// @AllArgsConstructor: 모든 필드를 받는 생성자 자동 생성
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstallationConfig {

    // DB 연결 설정 (설치 마법사 3단계에서 입력)
    private String dbType     = "MySQL";
    private String dbHost     = "localhost";
    private int    dbPort     = 0;
    private String dbName     = "nexis";
    private String dbUser     = "nexis";
    private String dbPassword = "";

    // Nexis 서버 연결 설정 (설치 마법사 4단계에서 입력)
    private String serverHost = "localhost";
    private int    serverPort = 9000;
    private String serverName = "Nexis Server";

    // 설치 완료 여부 플래그 — true면 설치 마법사 우회, DB 풀 초기화
    private boolean installed = false;
}