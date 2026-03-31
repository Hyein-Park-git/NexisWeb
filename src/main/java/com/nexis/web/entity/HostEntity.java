package com.nexis.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hosts")
public class HostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String hostname;

    private String  ipAddress;
    private String  os;
    private String  description;
    private Boolean enabled                = true;
    private Boolean agentActive            = false;
    private Boolean defaultGroupRegistered = false;

    private LocalDateTime agentLastCheck;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long   getId()                            { return id; }
    public void   setId(Long id)                     { this.id = id; }
    public String getHostname()                      { return hostname; }
    public void   setHostname(String v)              { this.hostname = v; }
    public String getIpAddress()                     { return ipAddress; }
    public void   setIpAddress(String v)             { this.ipAddress = v; }
    public String getOs()                            { return os; }
    public void   setOs(String v)                    { this.os = v; }
    public String getDescription()                   { return description; }
    public void   setDescription(String v)           { this.description = v; }
    public Boolean isEnabled()                       { return enabled != null ? enabled : true; }
    public void   setEnabled(Boolean v)              { this.enabled = v; }
    public Boolean isAgentActive()                   { return agentActive != null ? agentActive : false; }
    public void   setAgentActive(Boolean v)          { this.agentActive = v; }
    public Boolean isDefaultGroupRegistered()        { return defaultGroupRegistered != null ? defaultGroupRegistered : false; }
    public void   setDefaultGroupRegistered(Boolean v){ this.defaultGroupRegistered = v; }
    public LocalDateTime getAgentLastCheck()         { return agentLastCheck; }
    public void   setAgentLastCheck(LocalDateTime v) { this.agentLastCheck = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void   setCreatedAt(LocalDateTime v)      { this.createdAt = v; }
}