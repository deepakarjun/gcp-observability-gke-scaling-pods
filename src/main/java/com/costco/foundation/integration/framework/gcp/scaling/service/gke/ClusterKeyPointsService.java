package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterKeyPoints;

/**
 * Provides an aggregated "key points" summary for a GKE cluster.
 */
public interface ClusterKeyPointsService {

    /**
     * Computes cluster-wide key points across user namespaces.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @return the aggregated summary
     */
    ClusterKeyPoints getKeyPoints(String projectId, String clusterId);
}