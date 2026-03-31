package com.nexis.web.config;

import com.nexis.web.model.InstallationConfig;
import com.nexis.web.service.InstallationService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Logger;

@Configuration
public class DataSourceConfig {

    private final InstallationService installationService;

    // volatile: 멀티스레드 환경에서 이 변수의 최신값을 모든 스레드가 즉시 볼 수 있도록 보장
    private volatile HikariDataSource pool = null;

    public DataSourceConfig(InstallationService installationService) {
        this.installationService = installationService;
    }

    // @Bean: Spring 컨테이너에 DataSource를 등록. JPA/MyBatis 등이 이걸 주입받아 DB 연결에 사용
    @Bean
    public DataSource dataSource() {
        // 실제 HikariDataSource를 바로 반환하지 않고, 래퍼 객체를 반환
        // → 설치 완료 전에는 DB 연결을 막고, 설치 후 최초 요청 시점에 풀을 초기화하기 위해
        return new SmartDataSource();
    }

    // 설치 설정값을 읽어 HikariCP 커넥션 풀을 실제로 생성하는 메서드
    private HikariDataSource buildPool() {
        InstallationConfig config = installationService.loadConfig();
        String dbType = config.getDbType() != null ? config.getDbType().toLowerCase() : "mysql";

        String url;
        String driver;

        // DB 종류마다 드라이버 클래스명과 JDBC URL 형식이 다르므로 분기 처리
        switch (dbType) {
            case "mariadb" -> {
                driver = "org.mariadb.jdbc.Driver";
                url = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&serverTimezone=Asia/Seoul",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
            }
            case "postgresql" -> {
                driver = "org.postgresql.Driver";
                url = String.format("jdbc:postgresql://%s:%d/%s",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
            }
            default -> { // 기본값: MySQL
                driver = "com.mysql.cj.jdbc.Driver";
                url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true",
                        config.getDbHost(), config.getDbPort(), config.getDbName());
            }
        }

        HikariConfig hc = new HikariConfig();
        hc.setDriverClassName(driver);
        hc.setJdbcUrl(url);
        hc.setUsername(config.getDbUser());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(10);     // 커넥션 풀 최대 10개 유지
        hc.setMinimumIdle(2);          // 사용 안 할 때도 최소 2개는 열어둠
        hc.setConnectionTimeout(5000); // 커넥션 못 얻으면 5초 후 예외 발생
        hc.setPoolName("NexisPool");
        return new HikariDataSource(hc);
    }

    // DataSource 인터페이스를 직접 구현한 내부 래퍼 클래스
    // 설치 여부를 체크한 뒤 실제 커넥션을 풀에서 꺼내주는 역할
    class SmartDataSource implements DataSource {

        private Connection getConn() throws SQLException {
            // 설치가 안 된 상태면 DB 연결 자체를 차단
            if (!installationService.isInstalled()) {
                throw new SQLException("Nexis is not installed yet.");
            }
            // Double-checked locking 패턴:
            // 풀이 이미 있으면 synchronized 블록을 아예 안 들어가서 성능 낭비를 막고,
            // 없을 때만 동기화해서 딱 한 번만 풀을 생성함
            if (pool == null) {
                synchronized (DataSourceConfig.this) {
                    if (pool == null) pool = buildPool();
                }
            }
            return pool.getConnection();
        }

        @Override public Connection getConnection() throws SQLException { return getConn(); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return getConn(); }

        // 아래는 DataSource 인터페이스 구현에 필요한 보일러플레이트 메서드 (실제로는 사용 안 함)
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int s) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        @Override public <T> T unwrap(Class<T> i) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> i) { return false; }
    }
}