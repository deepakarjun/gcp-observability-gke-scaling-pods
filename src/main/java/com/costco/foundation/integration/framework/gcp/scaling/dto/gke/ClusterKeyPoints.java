package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

/**
 * Aggregated summary ("key points") for a selected GKE cluster.
 *
 * @param projectId         the GCP project
 * @param clusterName       the cluster the summary was computed for
 * @param userNamespaces    number of user-created namespaces (system excluded)
 * @param servicesRunning   deployments with at least one desired replica
 * @param servicesSuspended deployments scaled to zero replicas
 * @param activePods        pods currently in the {@code Running} phase
 * @param containers        total containers across all running pods
 */
public record ClusterKeyPoints(
        String projectId,
        String clusterName,
        int userNamespaces,
        int servicesRunning,
        int servicesSuspended,
        int activePods,
        int containers) {
}