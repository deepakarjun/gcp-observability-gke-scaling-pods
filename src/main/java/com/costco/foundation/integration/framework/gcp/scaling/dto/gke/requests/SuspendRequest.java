package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to suspend (scale to zero) a service.
 *
 * @param projectId   GKE project identifier
 * @param namespace   target namespace
 * @param serviceName target service/deployment name
 */
public record SuspendRequest(
        @NotBlank(message = "projectId is required") String projectId,
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "serviceName is required") String serviceName) {
}