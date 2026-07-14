package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

/**
 * Represents a single GKE cluster.
 *
 * @param name     the cluster name
 * @param location the cluster location (zone or region)
 * @param status   the cluster status (e.g. {@code RUNNING})
 */
public record ClusterInfo(String name, String location, String status) {
}
