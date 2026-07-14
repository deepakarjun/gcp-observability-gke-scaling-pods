package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.NamespaceListResponse;

/**
 * Provides discovery of GKE clusters and their user-created namespaces.
 */
public interface ClusterInfoService {

    /**
     * Lists all clusters in the given project.
     *
     * @param projectId the GCP project id
     * @return the clusters
     */
    ClusterListResponse getClusters(String projectId);

    /**
     * Lists user-created namespaces for a specific cluster.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @return the user namespaces
     */
    NamespaceListResponse getUserNamespaces(String projectId, String clusterId);
}