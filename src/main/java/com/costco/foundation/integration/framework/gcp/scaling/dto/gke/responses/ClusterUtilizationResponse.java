package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cluster utilization response bundling CPU, Memory, and Disk series over a
 * time window.
 *
 * @param projectId   the GCP project
 * @param clusterName the cluster
 * @param startTime   window start (UTC)
 * @param endTime     window end (UTC)
 * @param series      one entry per requested metric
 */
public record ClusterUtilizationResponse(
        String projectId,
        String clusterName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        List<MetricSeries> series) {
}
