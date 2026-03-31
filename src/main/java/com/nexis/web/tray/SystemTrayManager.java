package com.nexis.web.tray;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.net.URI;

@Component
public class SystemTrayManager {

    // application.properties의 server.port 값 주입, 없으면 기본값 8080
    @Value("${server.port:8080}")
    private int serverPort;

    private SystemTray tray;
    private TrayIcon   trayIcon;

    // @PostConstruct: 스프링 Bean 생성 + 의존성 주입 완료 후 자동 실행
    // 생성자에서 하면 @Value 주입 전이라 serverPort가 0이 될 수 있어서 여기서 처리
    @PostConstruct
    public void initTray() {
        try {
            // Linux는 트레이 아이콘 지원이 불안정해서 스킵
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                System.out.println("[Tray] Skipping tray icon on Linux.");
                return;
            }

            // 현재 환경이 시스템 트레이를 지원하는지 체크 (headless 환경 등)
            if (!SystemTray.isSupported()) {
                System.out.println("[Tray] SystemTray not supported.");
                return;
            }

            tray = SystemTray.getSystemTray();

            Image image = loadIcon();
            if (image == null) {
                System.err.println("[Tray] icon.png not found, skipping tray.");
                return;
            }

            PopupMenu popup = buildPopupMenu();

            trayIcon = new TrayIcon(image, "Nexis Web", popup);
            trayIcon.setImageAutoSize(true); // 트레이 크기에 맞게 아이콘 자동 리사이즈
            trayIcon.addActionListener(e -> openBrowser()); // 아이콘 더블클릭 시 브라우저 열기

            tray.add(trayIcon);
            System.out.println("[Tray] Tray icon initialized.");

        } catch (Exception e) {
            // 트레이 초기화 실패해도 서버는 정상 동작해야 하므로 예외를 삼킴
            System.err.println("[Tray] Failed to init tray: " + e.getMessage());
        }
    }

    // 아이콘 이미지 로딩 — 우선순위: JAR 내부 → 실행 경로
    private Image loadIcon() {
        // 1순위: JAR 내부 resources/icon.png (배포 환경)
        var url = getClass().getResource("/icon.png");
        if (url != null) {
            return Toolkit.getDefaultToolkit().getImage(url);
        }

        // 2순위: 실행 디렉토리(user.dir) 옆의 icon.png (개발 환경)
        File file = new File(System.getProperty("user.dir") + "/icon.png");
        if (file.exists()) {
            return Toolkit.getDefaultToolkit().getImage(file.getAbsolutePath());
        }

        return null; // 아이콘을 못 찾으면 null 반환 → 트레이 초기화 중단
    }

    // 우클릭 팝업 메뉴 생성
    private PopupMenu buildPopupMenu() {
        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("열기");
        openItem.addActionListener(e -> openBrowser()); // 브라우저로 웹 UI 열기

        MenuItem exitItem = new MenuItem("종료");
        exitItem.addActionListener(e -> shutdown()); // 앱 종료

        popup.add(openItem);
        popup.addSeparator(); // 구분선
        popup.add(exitItem);

        return popup;
    }

    // 기본 브라우저로 localhost 웹 UI 열기
    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + serverPort));
        } catch (Exception e) {
            System.err.println("[Tray] Failed to open browser: " + e.getMessage());
        }
    }

    // 트레이 아이콘 제거 후 프로세스 종료
    private void shutdown() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
        System.exit(0);
    }

    // @PreDestroy: 스프링 컨텍스트 종료 시 자동 호출 (정상 종료 시 트레이 아이콘 정리)
    @PreDestroy
    public void removeTray() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
    }
}