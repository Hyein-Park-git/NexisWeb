package com.nexis.web.service;

import com.nexis.web.model.Metric;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 현재는 로컬 JVM 지표 및 하드코딩된 더미 데이터를 제공하는 서비스
// 추후 실제 에이전트 연동으로 교체 가능한 구조
@Service
public class MonitoringService {

    // JVM이 실행 중인 서버의 CPU 사용률 반환 (0~100%)
    // com.sun.management.OperatingSystemMXBean: 표준 JDK에서 제공하는 시스템 레벨 CPU 정보
    public double getCpuUsage() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            double load = ((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad();
            if (load >= 0) return load * 100; // 0.0~1.0 → 0~100%로 변환
        }
        return 0;
    }

    // JVM 힙 기준 메모리 사용률 반환 (0~100%)
    // Runtime.getRuntime()으로 JVM 메모리 정보 조회
    public double getMemoryUsage() {
        Runtime runtime    = Runtime.getRuntime();
        long totalMemory   = runtime.totalMemory(); // 현재 JVM에 할당된 총 메모리
        long freeMemory    = runtime.freeMemory();  // 그 중 사용 가능한 메모리
        long usedMemory    = totalMemory - freeMemory;
        return usedMemory * 100.0 / totalMemory;
    }

    // DB 연결 상태 반환 (현재 하드코딩 — 실제 체크 로직으로 교체 가능)
    public String getDbStatus() {
        return "Connected";
    }

    // 현재 활성 사용자 수 (현재 하드코딩 — 실제 세션 기반으로 교체 가능)
    public int getActiveUsers() {
        return 5;
    }

    // 통계 요약 (현재 하드코딩 — 실제 DB 조회로 교체 가능)
    public Map<String, Object> getStatsSummary() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers",     20);
        stats.put("activeSessions", 5);
        stats.put("dbTables",       10);
        return stats;
    }

    // 최신 메트릭 리스트 (현재 더미 데이터 — 실제 에이전트 수집 데이터로 교체 예정)
    public List<Metric> getLatestMetrics() {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("Server-1", getCpuUsage(), getMemoryUsage(), "Windows 11",   LocalDateTime.now().minusMinutes(1)));
        metrics.add(new Metric("Server-2", 30.2,          55.5,             "Ubuntu 22.04", LocalDateTime.now().minusMinutes(2)));
        metrics.add(new Metric("Server-3", 70.1,          80.3,             "CentOS 8",     LocalDateTime.now().minusMinutes(3)));
        return metrics;
    }
}