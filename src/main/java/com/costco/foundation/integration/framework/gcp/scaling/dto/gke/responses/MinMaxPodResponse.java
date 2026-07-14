package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

/**
 * Minimum and maximum pod configuration for a service.
 *
 * @param projectId   GKE project identifier
 * @param namespace   target namespace
 * @param serviceName target service/deployment name
 * @param minPods     minimum configured replicas
 * @param maxPods     maximum configured replicas
 */
public record MinMaxPodResponse(
        String projectId,
        String namespace,
        String serviceName,
        int minPods,
        int maxPods) {
}