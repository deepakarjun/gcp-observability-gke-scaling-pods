package com.costco.foundation.integration.framework.gcp.scaling.entity;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent audit record capturing a single scaling-related operation
 * (scale, rollback, suspend, resume) and its API response payload.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_cluster", columnList = "cluster_name"),
        @Index(name = "idx_audit_service", columnList = "service_name"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})

@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditStatus status;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "cluster_name", nullable = false)
    private String clusterName;

    @Column(name = "namespace")
    private String namespace;

    @Column(name = "service_name")
    private String serviceName;

    /** The full API response (or error detail) captured as JSON. */
    @Type(JsonType.class)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Object responsePayload;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Required by JPA. */
    public AuditLog() {
    }

    // Getters/setters omitted here for brevity — generate via IDE/Lombok.
    // (Prefer a builder; see AuditLogBuilder note in recommendations.)

    // ...getters and setters...
}