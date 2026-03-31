package com.nexis.web.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "host_group_members")
public class HostGroupMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long hostId;

    public Long getId()            { return id; }
    public void setId(Long id)     { this.id = id; }
    public Long getGroupId()       { return groupId; }
    public void setGroupId(Long v) { this.groupId = v; }
    public Long getHostId()        { return hostId; }
    public void setHostId(Long v)  { this.hostId = v; }
}