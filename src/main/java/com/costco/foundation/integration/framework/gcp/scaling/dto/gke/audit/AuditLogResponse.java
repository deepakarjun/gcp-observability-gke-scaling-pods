package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit;


import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit log entry returned by the audit query API.
 */
public record AuditLogResponse(
        UUID id,
        AuditAction action,
        AuditStatus status,
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        Object responsePayload,
        String errorMessage,
        OffsetDateTime createdAt) {
}
