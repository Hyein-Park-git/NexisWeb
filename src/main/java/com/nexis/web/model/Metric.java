package com.nexis.web.model;

import java.time.LocalDateTime;

// Metric: 에이전트로부터 수집한 호스트의 실시간 지표를 담는 모델 클래스
// DB 엔티티가 아닌 순수 데이터 전달용 객체 (POJO)
public class Metric {

    private String        hostname;     // 수집 대상 호스트명
    private double        cpuUsage;     // CPU 사용률 (%)
    private double        memoryUsage;  // 메모리 사용률 (%)
    private String        os;           // 운영체제 정보
    private LocalDateTime collectedAt;  // 수집 시각
    private long          totalMemory;  // 전체 메모리 (bytes)
    private long          freeMemory;   // 여유 메모리 (bytes)

    // 메모리 상세 없이 기본 지표만 담는 생성자
    public Metric(String hostname, double cpuUsage, double memoryUsage,
                  String os, LocalDateTime collectedAt) {
        this.hostname    = hostname;
        this.cpuUsage    = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.os          = os;
        this.collectedAt = collectedAt;
    }

    // 메모리 상세(total/free)까지 포함한 전체 생성자
    public Metric(String hostname, double cpuUsage, double memoryUsage,
                  String os, LocalDateTime collectedAt,
                  long totalMemory, long freeMemory) {
        this.hostname     = hostname;
        this.cpuUsage     = cpuUsage;
        this.memoryUsage  = memoryUsage;
        this.os           = os;
        this.collectedAt  = collectedAt;
        this.totalMemory  = totalMemory;
        this.freeMemory   = freeMemory;
    }

    // Getter & Setter
    public String getHostname()                       { return hostname; }
    public void   setHostname(String hostname)        { this.hostname = hostname; }

    public double getCpuUsage()                       { return cpuUsage; }
    public void   setCpuUsage(double cpuUsage)        { this.cpuUsage = cpuUsage; }

    public double getMemoryUsage()                    { return memoryUsage; }
    public void   setMemoryUsage(double memoryUsage)  { this.memoryUsage = memoryUsage; }

    public String getOs()                             { return os; }
    public void   setOs(String os)                    { this.os = os; }

    public LocalDateTime getCollectedAt()                          { return collectedAt; }
    public void          setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }

    public long getTotalMemory()                      { return totalMemory; }
    public void setTotalMemory(long totalMemory)      { this.totalMemory = totalMemory; }

    public long getFreeMemory()                       { return freeMemory; }
    public void setFreeMemory(long freeMemory)        { this.freeMemory = freeMemory; }
}