package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ServiceState;

import java.time.OffsetDateTime;

/**
 * Standard response after a service lifecycle operation.
 *
 * @param projectId   GKE project identifier
 * @param namespace   target namespace
 * @param serviceName target service/deployment name
 * @param state       resulting lifecycle state
 * @param replicas    resulting replica count
 * @param message     human readable status message
 * @param timestamp   operation time
 */
public record ServiceStateResponse(
        String projectId,
        String namespace,
        String serviceName,
        ServiceState state,
        int replicas,
        String message,
        OffsetDateTime timestamp) {

    public static ServiceStateResponse of(String projectId, String namespace, String serviceName,
                                          ServiceState state, int replicas, String message) {
        return new ServiceStateResponse(projectId, namespace, serviceName, state, replicas,
                message, OffsetDateTime.now());
    }
}