package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service (container) utilization response bundling CPU, Memory, and Disk
 * series over a time window for a specific running service.
 *
 * @param projectId   the GCP project
 * @param clusterName the cluster
 * @param namespace   the namespace containing the service
 * @param serviceName the service (container/Deployment) name
 * @param startTime   window start (UTC)
 * @param endTime     window end (UTC)
 * @param series      one entry per requested metric
 */
public record ServiceUtilizationResponse(
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        List<MetricSeries> series) {
}