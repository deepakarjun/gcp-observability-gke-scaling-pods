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

import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @Type(JsonType.class)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Object responsePayload;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Required by JPA. */
    protected AuditLog() {
    }

    private AuditLog(Builder builder) {
        this.action = builder._action;
        this.status = builder._status;
        this.projectId = builder._projectId;
        this.clusterName = builder._clusterName;
        this.namespace = builder._namespace;
        this.serviceName = builder._serviceName;
        this.responsePayload = builder._responsePayload;
        this.errorMessage = builder._errorMessage;
        this.createdAt = builder._createdAt != null
                ? builder._createdAt : OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** @return a new {@link Builder} for constructing audit records */
    public static Builder builder() {
        return new Builder();
    }

    // --- Getters (no setters: entity is built via the Builder) ---

    public UUID getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Object getResponsePayload() {
        return responsePayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Fluent builder for {@link AuditLog}, keeping construction immutable and
     * readable at call sites.
     */
    public static final class Builder {

        private AuditAction _action;
        private AuditStatus _status;
        private String _projectId;
        private String _clusterName;
        private String _namespace;
        private String _serviceName;
        private Object _responsePayload;
        private String _errorMessage;
        private OffsetDateTime _createdAt;

        private Builder() {
        }

        public Builder action(AuditAction action) {
            _action = action;
            return this;
        }

        public Builder status(AuditStatus status) {
            _status = status;
            return this;
        }

        public Builder projectId(String projectId) {
            _projectId = projectId;
            return this;
        }

        public Builder clusterName(String clusterName) {
            _clusterName = clusterName;
            return this;
        }

        public Builder namespace(String namespace) {
            _namespace = namespace;
            return this;
        }

        public Builder serviceName(String serviceName) {
            _serviceName = serviceName;
            return this;
        }

        public Builder responsePayload(Object responsePayload) {
            _responsePayload = responsePayload;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            _errorMessage = errorMessage;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            _createdAt = createdAt;
            return this;
        }

        /** @return a fully constructed {@link AuditLog} */
        public AuditLog build() {
            return new AuditLog(this);
        }
    }
}