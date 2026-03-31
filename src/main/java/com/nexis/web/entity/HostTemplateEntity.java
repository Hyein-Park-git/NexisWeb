package com.nexis.web.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "host_templates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"host_id", "template_id"}))
public class HostTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long hostId;
    @Column(nullable = false)
    private Long templateId;
    private LocalDateTime linkedAt;
    @PrePersist
    public void prePersist() { this.linkedAt = LocalDateTime.now(); }
    public Long getId()                      { return id; }
    public void setId(Long id)               { this.id = id; }
    public Long getHostId()                  { return hostId; }
    public void setHostId(Long v)            { this.hostId = v; }
    public Long getTemplateId()              { return templateId; }
    public void setTemplateId(Long v)        { this.templateId = v; }
    public LocalDateTime getLinkedAt()       { return linkedAt; }
    public void setLinkedAt(LocalDateTime v) { this.linkedAt = v; }
}