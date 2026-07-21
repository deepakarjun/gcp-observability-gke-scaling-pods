package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;

/**
 * Immutable command describing an operation to be recorded in the audit log.
 *
 * @param action          the audited operation
 * @param status          the outcome
 * @param projectId       the GCP project
 * @param clusterName     the cluster
 * @param namespace       the namespace (nullable for cluster-scoped actions)
 * @param serviceName     the service (nullable for cluster-scoped actions)
 * @param responsePayload the API response or error detail to persist
 * @param errorMessage    optional error message when status is FAILURE
 */
public record AuditRecordCommand(
        AuditAction action,
        AuditStatus status,
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        Object responsePayload,
        String errorMessage) {
}