package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Result of a service rollback operation.
 *
 * @param projectId        the GCP project
 * @param clusterName      the cluster
 * @param namespace        the namespace containing the service
 * @param serviceName      the Deployment (service) name
 * @param previousRevision the revision that was active before rollback
 * @param rolledBackTo     the revision that was restored
 * @param restoredImages   container images restored by the rollback
 * @param rolledBackAt     when the rollback was applied
 */
public record RollbackResult(
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        long previousRevision,
        long rolledBackTo,
        List<String> restoredImages,
        OffsetDateTime rolledBackAt) {
}