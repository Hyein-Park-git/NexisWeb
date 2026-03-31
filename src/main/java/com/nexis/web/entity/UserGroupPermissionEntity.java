package com.nexis.web.entity;
import jakarta.persistence.*;
@Entity
@Table(name = "user_group_permissions")
public class UserGroupPermissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    @Column(name = "resource", nullable = false)
    private String resource; // dashboard, monitoring, configuration, administration
    @Column(name = "access_level", nullable = false)
    private String accessLevel = "none"; // none, read, write
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
}