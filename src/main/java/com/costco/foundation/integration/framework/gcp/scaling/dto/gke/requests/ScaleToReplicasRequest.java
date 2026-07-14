package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to scale a service to a specific replica count.
 * The desired count is validated against the service's HPA min/max bounds.
 *
 * @param projectId       GKE project identifier
 * @param namespace       target namespace
 * @param serviceName     target service/deployment name
 * @param desiredReplicas desired replica count (must be within HPA min/max)
 */
public record ScaleToReplicasRequest(
        @NotBlank(message = "projectId is required") String projectId,
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "serviceName is required") String serviceName,
        @NotNull(message = "desiredReplicas is required")
        @Min(value = 1, message = "desiredReplicas must be >= 1") Integer desiredReplicas) {
}