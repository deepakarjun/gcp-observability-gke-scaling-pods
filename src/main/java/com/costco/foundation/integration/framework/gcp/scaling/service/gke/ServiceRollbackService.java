package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.RollbackRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.RollbackResult;

/**
 * Rolls a service (Deployment) back to a previous rollout revision.
 */
public interface ServiceRollbackService {

    /**
     * Rolls the service back to the requested revision, or to the immediately
     * previous revision when none is specified.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the Deployment (service) name
     * @param request     the rollback request (optional target revision)
     * @return the rollback result
     */
    RollbackResult rollback(
            String projectId, 
            String clusterId, 
            String namespace,
            String serviceName, 
            RollbackRequest request
            );
}