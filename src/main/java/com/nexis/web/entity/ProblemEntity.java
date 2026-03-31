package com.nexis.web.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "problems")
public class ProblemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long triggerId;
    @Column(nullable = false)
    private Long hostId;
    private String hostname;
    private String triggerName;
    private String itemName;
    private String expression;
    private String severity;
    private String value;
    @Column(nullable = false)
    private String status = "PROBLEM";
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    @PrePersist
    public void prePersist() { if (this.startedAt == null) this.startedAt = LocalDateTime.now(); }
    public Long   getId()                       { return id; }
    public void   setId(Long v)                 { this.id = v; }
    public Long   getTriggerId()                { return triggerId; }
    public void   setTriggerId(Long v)          { this.triggerId = v; }
    public Long   getHostId()                   { return hostId; }
    public void   setHostId(Long v)             { this.hostId = v; }
    public String getHostname()                 { return hostname; }
    public void   setHostname(String v)         { this.hostname = v; }
    public String getTriggerName()              { return triggerName; }
    public void   setTriggerName(String v)      { this.triggerName = v; }
    public String getItemName()                 { return itemName; }
    public void   setItemName(String v)         { this.itemName = v; }
    public String getExpression()               { return expression; }
    public void   setExpression(String v)       { this.expression = v; }
    public String getSeverity()                 { return severity; }
    public void   setSeverity(String v)         { this.severity = v; }
    public String getValue()                    { return value; }
    public void   setValue(String v)            { this.value = v; }
    public String getStatus()                   { return status; }
    public void   setStatus(String v)           { this.status = v; }
    public LocalDateTime getStartedAt()         { return startedAt; }
    public void   setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getResolvedAt()        { return resolvedAt; }
    public void   setResolvedAt(LocalDateTime v){ this.resolvedAt = v; }
}