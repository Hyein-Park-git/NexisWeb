package com.nexis.web.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "item_data")
public class ItemDataEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "item_id", nullable = false)
    private Long itemId;
    @Column(name = "hostname", nullable = false)
    private String hostname;
    @Column(name = "value")
    private Double value;
    @Column(name = "collected_at")
    private LocalDateTime collectedAt;
    @PrePersist
    public void prePersist() { if (this.collectedAt == null) this.collectedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
}