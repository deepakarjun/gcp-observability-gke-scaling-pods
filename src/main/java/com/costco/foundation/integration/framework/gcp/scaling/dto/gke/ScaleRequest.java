package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

//public record ScaleRequest() {
//
//}
//
//
//package com.costco.scaling.dto;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ScaleDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to scale a Kubernetes deployment.
 *
 * @param namespace      target namespace
 * @param deploymentName target deployment name
 * @param direction      scale UP or DOWN
 * @param replicas       desired replica count
 */
public record ScaleRequest(
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "deploymentName is required") String deploymentName,
        @NotNull(message = "direction is required") ScaleDirection direction,
        @Min(value = 0, message = "replicas must be >= 0") int replicas) {
}