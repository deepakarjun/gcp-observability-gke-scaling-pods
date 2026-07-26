package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

/**
 * Details of the HorizontalPodAutoscaler associated with a service.
 *
 * @param projectId       the GCP project
 * @param clusterName     the cluster
 * @param namespace       the namespace containing the service
 * @param serviceName     the service (Deployment) name the HPA targets
 * @param hpaName         the HPA resource name; {@code null} when none exists
 * @param targetKind      the {@code scaleTargetRef} kind (e.g. Deployment)
 * @param minReplicas     the HPA minimum replicas
 * @param maxReplicas     the HPA maximum replicas
 * @param currentReplicas the currently observed replicas
 * @param present         whether an HPA is configured for the service
 */
public record ServiceHpaResponse(
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        String hpaName,
        String targetKind,
        Integer minReplicas,
        Integer maxReplicas,
        Integer currentReplicas,
        boolean present) {
}