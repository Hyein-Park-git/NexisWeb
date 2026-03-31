package com.nexis.web.entity;
import jakarta.persistence.*;
@Entity
@Table(name = "user_group_host_group_access")
public class UserGroupHostAccessEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    @Column(name = "host_group_id", nullable = false)
    private Long hostGroupId;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getHostGroupId() { return hostGroupId; }
    public void setHostGroupId(Long hostGroupId) { this.hostGroupId = hostGroupId; }
}