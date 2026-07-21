package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;

import java.time.OffsetDateTime;

/**
 * Optional filter criteria for querying audit logs. Any {@code null} field is
 * ignored, allowing flexible combinations.
 *
 * @param action      filter by operation
 * @param status      filter by outcome
 * @param projectId   filter by project
 * @param clusterName filter by cluster
 * @param namespace   filter by namespace
 * @param serviceName filter by service
 * @param startTime   inclusive lower bound for {@code createdAt}
 * @param endTime     exclusive upper bound for {@code createdAt}
 */
public record AuditLogFilter(
        AuditAction action,
        AuditStatus status,
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        OffsetDateTime startTime,
        OffsetDateTime endTime) {
}