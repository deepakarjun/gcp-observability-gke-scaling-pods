package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceHpaResponse;

/**
 * Resolves the HorizontalPodAutoscaler associated with a running service.
 */
public interface ServiceHpaService {

    /**
     * Finds the HPA that targets the given service (Deployment) in a namespace.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the service (Deployment) name
     * @return the HPA details; {@link ServiceHpaResponse#present()} is
     *         {@code false} when no HPA targets the service
     */
    ServiceHpaResponse getHpaForService(
            String projectId, String clusterId, String namespace, String serviceName);
}