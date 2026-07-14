package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.SuspendedServiceListResponse;

/**
 * Provides discovery of suspended (scaled-to-zero) GKE services.
 */
public interface SuspendedServiceInfoService {

    /**
     * Lists suspended services (deployments with zero desired replicas)
     * in the given cluster namespace.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @param namespace the namespace to inspect
     * @return the suspended services
     */
    SuspendedServiceListResponse getSuspendedServices(String projectId, String clusterId, String namespace);
}