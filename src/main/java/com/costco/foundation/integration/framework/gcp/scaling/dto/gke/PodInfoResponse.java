package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

/**
 * Number of currently running pods for a service.
 *
 * @param projectId    GKE project identifier
 * @param namespace    target namespace
 * @param serviceName  target service/deployment name
 * @param runningPods  count of pods currently running
 */
public record PodInfoResponse(
        String projectId,
        String namespace,
        String serviceName,
        int runningPods) {
}