package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.util.List;

/**
 * Response for the "list clusters in a project" endpoint.
 *
 * @param projectId    the GCP project the clusters belong to
 * @param clusterCount number of clusters returned
 * @param clusters     the clusters
 */
public record ClusterListResponse(
        String projectId,
        int clusterCount,
        List<ClusterInfo> clusters) {
}