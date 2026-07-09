package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

/**
 * Represents a suspended (scaled-to-zero) GKE service.
 *
 * @param name              the deployment/service name
 * @param namespace         the namespace it belongs to
 * @param desiredReplicas   configured replicas (0 when suspended)
 * @param availableReplicas currently available replicas
 */
public record SuspendedServiceInfo(
        String name,
        String namespace,
        int desiredReplicas,
        int availableReplicas) {
}