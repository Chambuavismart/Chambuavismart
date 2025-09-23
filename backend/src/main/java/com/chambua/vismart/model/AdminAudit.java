package com.chambua.vismart.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit", indexes = {
        @Index(name = "idx_admin_audit_ts", columnList = "timestamp")
})
public class AdminAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "params", columnDefinition = "text")
    private String params;

    @Column(name = "affected_count")
    private Long affectedCount;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }

    public Long getAffectedCount() { return affectedCount; }
    public void setAffectedCount(Long affectedCount) { this.affectedCount = affectedCount; }
}
