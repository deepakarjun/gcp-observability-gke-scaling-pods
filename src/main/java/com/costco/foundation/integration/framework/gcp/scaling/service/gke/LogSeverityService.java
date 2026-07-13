package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;

/**
 * Provides Cloud Logging severity counts for a GKE cluster.
 */
public interface LogSeverityService {

    /**
     * Counts log entries by severity for the given cluster over the last week.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name (matched on {@code resource.labels.cluster_name})
     * @return the severity counts
     */
    LogSeverityCounts getWeeklySeverityCounts(String projectId, String clusterId);
}