package com.nexis.web.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "templates")
public class TemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    private String description;
    private String osType = "Any";
    private Boolean checkCpu     = true;
    private Boolean checkMemory  = true;
    private Boolean checkDisk    = false;
    private Boolean checkNetwork = false;
    private Boolean checkProcess = false;
    private Integer cpuThreshold    = 90;
    private Integer memoryThreshold = 90;
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOsType() { return osType; }
    public void setOsType(String osType) { this.osType = osType; }
    public Boolean getCheckCpu() { return checkCpu != null ? checkCpu : true; }
    public void setCheckCpu(Boolean v) { this.checkCpu = v; }
    public Boolean getCheckMemory() { return checkMemory != null ? checkMemory : true; }
    public void setCheckMemory(Boolean v) { this.checkMemory = v; }
    public Boolean getCheckDisk() { return checkDisk != null ? checkDisk : false; }
    public void setCheckDisk(Boolean v) { this.checkDisk = v; }
    public Boolean getCheckNetwork() { return checkNetwork != null ? checkNetwork : false; }
    public void setCheckNetwork(Boolean v) { this.checkNetwork = v; }
    public Boolean getCheckProcess() { return checkProcess != null ? checkProcess : false; }
    public void setCheckProcess(Boolean v) { this.checkProcess = v; }
    public Integer getCpuThreshold() { return cpuThreshold != null ? cpuThreshold : 90; }
    public void setCpuThreshold(Integer v) { this.cpuThreshold = v; }
    public Integer getMemoryThreshold() { return memoryThreshold != null ? memoryThreshold : 90; }
    public void setMemoryThreshold(Integer v) { this.memoryThreshold = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}