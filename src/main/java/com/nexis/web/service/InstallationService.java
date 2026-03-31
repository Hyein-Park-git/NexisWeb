package com.nexis.web.service;

import com.nexis.web.model.InstallationConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@Service
public class InstallationService {

    // @Value: application.properties의 값을 주입. 없으면 기본값 "./nexis.conf" 사용
    @Value("${nexis.config.path:./nexis.conf}")
    private String configPathRaw;

    private String resolvedConfigPath = null;

    // 설정 파일 절대 경로를 한 번만 계산하고 캐싱 (중복 계산 방지)
    private String getConfigPath() {
        if (resolvedConfigPath != null) return resolvedConfigPath;
        File f = new File(configPathRaw);
        if (f.isAbsolute()) {
            resolvedConfigPath = f.getAbsolutePath();
            return resolvedConfigPath;
        }
        // 상대 경로면 실행 디렉토리(user.dir) 기준으로 절대 경로 계산
        resolvedConfigPath = new File(System.getProperty("user.dir"), "nexis.conf").getAbsolutePath();
        return resolvedConfigPath;
    }

    // 설치 완료 여부 확인 — 파일 존재 + installed=true 여부 확인
    public boolean isInstalled() {
        File configFile = new File(getConfigPath());
        if (!configFile.exists()) return false;
        try {
            Properties props = loadProperties();
            return "true".equals(props.getProperty("installed"));
        } catch (Exception e) {
            return false;
        }
    }

    // 설정 파일을 읽어 InstallationConfig 객체로 반환
    // 파일이 없거나 읽기 실패 시 기본값이 설정된 객체 반환
    public InstallationConfig loadConfig() {
        InstallationConfig config = new InstallationConfig();
        try {
            Properties props = loadProperties();
            config.setDbType(props.getProperty("db.type", "MySQL"));
            config.setDbHost(props.getProperty("db.host", "localhost"));
            config.setDbPort(Integer.parseInt(props.getProperty("db.port", "3306")));
            config.setDbName(props.getProperty("db.name", "nexis"));
            config.setDbUser(props.getProperty("db.user", "nexis"));
            config.setDbPassword(props.getProperty("db.password", ""));
            config.setServerHost(props.getProperty("server.host", "localhost"));
            config.setServerPort(Integer.parseInt(props.getProperty("server.port", "9000")));
            config.setServerName(props.getProperty("server.name", "Nexis Server"));
            config.setInstalled("true".equals(props.getProperty("installed")));
        } catch (Exception e) {
            // 파일 없거나 파싱 실패 시 기본값 그대로 반환
        }
        return config;
    }

    // InstallationConfig를 nexis.conf 파일에 저장
    public void saveConfig(InstallationConfig config) throws IOException {
        Properties props = new Properties();
        props.setProperty("db.type",     nvl(config.getDbType(),     "MySQL"));
        props.setProperty("db.host",     nvl(config.getDbHost(),     ""));
        props.setProperty("db.port",     String.valueOf(config.getDbPort()));
        props.setProperty("db.name",     nvl(config.getDbName(),     ""));
        props.setProperty("db.user",     nvl(config.getDbUser(),     ""));
        props.setProperty("db.password", nvl(config.getDbPassword(), ""));
        props.setProperty("server.host", nvl(config.getServerHost(), ""));
        props.setProperty("server.port", String.valueOf(config.getServerPort()));
        props.setProperty("server.name", nvl(config.getServerName(), "Nexis Server"));
        props.setProperty("installed",   String.valueOf(config.isInstalled()));

        File configFile = new File(getConfigPath());
        // 상위 디렉토리가 없으면 자동 생성
        if (configFile.getParentFile() != null) configFile.getParentFile().mkdirs();

        // try-with-resources: 자동으로 스트림 close 보장
        try (FileOutputStream fos = new FileOutputStream(getConfigPath())) {
            props.store(fos, "Nexis Web Configuration");
        }
    }

    // DB 연결 테스트 — DB 종류에 따라 드라이버와 URL을 다르게 설정
    public boolean checkDbConnection(InstallationConfig config) {
        String dbType = config.getDbType() != null ? config.getDbType().toLowerCase() : "mysql";
        try {
            String url, driver;
            switch (dbType) {
                case "mariadb" -> {
                    driver = "org.mariadb.jdbc.Driver";
                    url = String.format("jdbc:mariadb://%s:%d/%s?connectTimeout=3000",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
                }
                case "postgresql" -> {
                    driver = "org.postgresql.Driver";
                    url = String.format("jdbc:postgresql://%s:%d/%s?connectTimeout=3",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
                }
                default -> {
                    driver = "com.mysql.cj.jdbc.Driver";
                    url = String.format("jdbc:mysql://%s:%d/%s?connectTimeout=3000&allowPublicKeyRetrieval=true",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
                }
            }
            Class.forName(driver); // 드라이버 클래스 로딩
            try (Connection conn = DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword())) {
                return conn.isValid(3); // 3초 안에 연결 유효하면 true
            }
        } catch (Exception e) {
            return false;
        }
    }

    // 서버 연결 테스트 — TCP 소켓으로 host:port 연결 가능 여부 확인 (3초 타임아웃)
    public boolean checkServerConnection(String host, int port) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 설치 전 사전 조건 체크 — Java 버전(17+), 설정 파일 쓰기 권한 확인
    public PrerequisiteCheck checkPrerequisites() {
        PrerequisiteCheck check = new PrerequisiteCheck();

        // Java 버전 체크
        String javaVersion = System.getProperty("java.version");
        check.setJavaVersion(javaVersion);
        try {
            // "1.8" → "8", "17.0.1" → "17" 파싱
            String v = javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion;
            int major = Integer.parseInt(v.split("[.\\-+]")[0]);
            check.setJavaOk(major >= 17);
            check.setJavaMinVersion(17);
            check.setJavaMajorVersion(major);
        } catch (Exception e) {
            check.setJavaOk(false);
        }

        // 설정 파일 쓰기 권한 체크 — 임시 파일 생성 시도
        File configFile = new File(getConfigPath());
        File parent = configFile.getParentFile();
        if (parent == null) parent = new File(".");
        boolean writable = false;
        try {
            if (!parent.exists()) parent.mkdirs();
            File tmp = File.createTempFile(".nexis_write_test", null, parent);
            tmp.delete();
            writable = true;
        } catch (Exception ignored) {}

        check.setConfigWritable(writable);
        check.setConfigPath(configFile.getAbsolutePath());
        return check;
    }

    // 설정 파일을 Properties 객체로 로딩하는 내부 헬퍼
    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(getConfigPath())) {
            props.load(fis);
        }
        return props;
    }

    // null 안전 처리 — value가 null이면 def(기본값) 반환
    private String nvl(String value, String def) {
        return (value != null) ? value : def;
    }

    // 사전 요구사항 체크 결과를 담는 내부 DTO 클래스
    public static class PrerequisiteCheck {
        private String  javaVersion;
        private boolean javaOk;
        private int     javaMinVersion;
        private int     javaMajorVersion;
        private boolean configWritable;
        private String  configPath;

        public String  getJavaVersion()             { return javaVersion; }
        public void    setJavaVersion(String v)     { this.javaVersion = v; }
        public boolean isJavaOk()                   { return javaOk; }
        public void    setJavaOk(boolean v)         { this.javaOk = v; }
        public int     getJavaMinVersion()           { return javaMinVersion; }
        public void    setJavaMinVersion(int v)     { this.javaMinVersion = v; }
        public int     getJavaMajorVersion()         { return javaMajorVersion; }
        public void    setJavaMajorVersion(int v)   { this.javaMajorVersion = v; }
        public boolean isConfigWritable()            { return configWritable; }
        public void    setConfigWritable(boolean v) { this.configWritable = v; }
        public String  getConfigPath()               { return configPath; }
        public void    setConfigPath(String v)      { this.configPath = v; }

        // 모든 체크를 통과했는지 여부
        public boolean isAllOk() { return javaOk && configWritable; }
    }
}