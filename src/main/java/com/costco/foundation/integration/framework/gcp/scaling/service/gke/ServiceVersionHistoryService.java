package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceVersionHistory;

/**
 * Provides rollout/version history for a service (Deployment) in a cluster.
 */
public interface ServiceVersionHistoryService {

    /**
     * Returns the version history for a service, derived from its ReplicaSet
     * revisions (newest first).
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the Deployment (service) name
     * @return the version history
     */
    ServiceVersionHistory getVersionHistory( String projectId, String clusterId, String namespace, String serviceName );
}