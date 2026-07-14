package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to resume a previously suspended service.
 * When {@code replicas} is null, the HPA minimum (or a safe default) is used.
 *
 * @param projectId   GKE project identifier
 * @param namespace   target namespace
 * @param serviceName target service/deployment name
 * @param replicas    optional desired replica count on resume
 */
public record ResumeRequest(
        @NotBlank(message = "projectId is required") String projectId,
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "serviceName is required") String serviceName,
        @Min(value = 1, message = "replicas must be >= 1") Integer replicas) {
}