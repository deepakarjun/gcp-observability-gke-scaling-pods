package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import java.util.List;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.LogSeverityCounts;

/**
 * Provides Cloud Logging severity counts for the workloads of a GKE cluster.
 */
public interface LogSeverityService {

    /**
     * Counts log entries by severity over the last week, scoped to the given
     * user namespaces (i.e. service workloads) of a cluster.
     *
     * @param projectId      the GCP project id
     * @param clusterId      the cluster name (matched on {@code resource.labels.cluster_name})
     * @param userNamespaces the user namespaces whose service logs should be counted
     * @return the severity counts; all-zero if no namespaces are supplied
     */
    LogSeverityCounts getWeeklySeverityCounts(
            String projectId, String clusterId, List<String> userNamespaces);
}