package com.nexis.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "items")
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "item_key")
    private String itemKey;

    @Column(nullable = false)
    private String metric;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(nullable = false)
    private String unit = "float";

    @Column(name = "unit_display")
    private String unitDisplay;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "host_id")
    private Long hostId;

    @Column(name = "source_template_id")
    private Long sourceTemplateId;

    private boolean enabled = true;

    private String description;

    // 수집 주기 (초 단위) — 기본값 60초
    // 에이전트 전송 주기보다 짧게 설정해도 실제 수집은 에이전트 주기에 종속되므로
    // Inactive 판단 시 오차(tolerance)를 감안해서 처리
    @Column(name = "`interval`", nullable = false)
    private int interval = 60;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public String getName()                      { return name; }
    public void setName(String name)             { this.name = name; }

    public String getItemKey()                   { return itemKey; }
    public void setItemKey(String itemKey)       { this.itemKey = itemKey; }

    public String getMetric()                    { return metric; }
    public void setMetric(String metric)         { this.metric = metric; }

    public String getValueType()                 { return valueType; }
    public void setValueType(String valueType)   { this.valueType = valueType; }

    public String getUnit()                      { return unit; }
    public void setUnit(String unit)             { this.unit = unit; }

    public String getUnitDisplay()               { return unitDisplay; }
    public void setUnitDisplay(String unitDisplay) { this.unitDisplay = unitDisplay; }

    public Long getTemplateId()                  { return templateId; }
    public void setTemplateId(Long templateId)   { this.templateId = templateId; }

    public Long getHostId()                      { return hostId; }
    public void setHostId(Long hostId)           { this.hostId = hostId; }

    public Long getSourceTemplateId()            { return sourceTemplateId; }
    public void setSourceTemplateId(Long sourceTemplateId) { this.sourceTemplateId = sourceTemplateId; }

    public boolean isEnabled()                   { return enabled; }
    public void setEnabled(boolean enabled)      { this.enabled = enabled; }

    public String getDescription()               { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getInterval()                     { return interval; }
    public void setInterval(int interval)        { this.interval = interval; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // unitLabel: unitDisplay가 있으면 사용, 없으면 unit 반환 (뷰 표시용)
    public String getUnitLabel() {
        return unitDisplay != null && !unitDisplay.isBlank() ? unitDisplay : unit;
    }
}