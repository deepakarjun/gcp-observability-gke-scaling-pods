package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.util.List;

/**
 * Response for the "list suspended services" endpoint.
 *
 * @param projectId         the GCP project
 * @param clusterName       the cluster queried
 * @param namespace         the namespace queried
 * @param serviceCount      number of suspended services returned
 * @param suspendedServices the suspended (scaled-to-zero) services
 */
public record SuspendedServiceListResponse(
        String projectId,
        String clusterName,
        String namespace,
        int serviceCount,
        List<SuspendedServiceInfo> suspendedServices) {
}