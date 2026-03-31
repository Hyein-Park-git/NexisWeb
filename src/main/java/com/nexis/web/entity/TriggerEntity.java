package com.nexis.web.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "triggers")
public class TriggerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private Long itemId;
    private Long hostId;
    private Long templateId;
    private Long sourceTemplateId;
    @Column(length = 1000)
    private String expression;
    @Column(nullable = false)
    private String func = "last";
    @Column(nullable = false)
    private String operator = ">";
    @Column(nullable = false)
    private Double threshold;
    @Column(nullable = false)
    private String severity = "Not classified";
    private Boolean enabled = true;
    private String  description;
    /** 지속 시간 조건 (분 단위, 0 또는 null = 즉시 발화) */
    private Integer duration = 0;
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
    public Long    getId()                       { return id; }
    public void    setId(Long v)                 { this.id = v; }
    public String  getName()                     { return name; }
    public void    setName(String v)             { this.name = v; }
    public Long    getItemId()                   { return itemId; }
    public void    setItemId(Long v)             { this.itemId = v; }
    public Long    getHostId()                   { return hostId; }
    public void    setHostId(Long v)             { this.hostId = v; }
    public Long    getTemplateId()               { return templateId; }
    public void    setTemplateId(Long v)         { this.templateId = v; }
    public Long    getSourceTemplateId()         { return sourceTemplateId; }
    public void    setSourceTemplateId(Long v)   { this.sourceTemplateId = v; }
    public String  getFunc()                     { return func; }
    public void    setFunc(String v)             { this.func = v; }
    public String  getOperator()                 { return operator; }
    public void    setOperator(String v)         { this.operator = v; }
    public Double  getThreshold()                { return threshold; }
    public void    setThreshold(Double v)        { this.threshold = v; }
    public String  getSeverity()                 { return severity; }
    public void    setSeverity(String v)         { this.severity = v; }
    public Boolean isEnabled()                   { return enabled != null ? enabled : true; }
    public void    setEnabled(Boolean v)         { this.enabled = v; }
    public String  getDescription()              { return description; }
    public void    setDescription(String v)      { this.description = v; }
    public Integer getDuration()                 { return duration != null ? duration : 0; }
    public void    setDuration(Integer v)        { this.duration = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public String  getExpression()               { return expression; }
    public void    setExpression(String v)       { this.expression = v; }
}