package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

//public record ScaleResponse() {
//
//}
//
//package com.costco.scaling.dto;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ScaleDirection;

import java.time.OffsetDateTime;

/**
 * Standard response after a scaling operation.
 *
 * @param namespace      target namespace
 * @param deploymentName target deployment name
 * @param direction      scaling direction performed
 * @param replicas       resulting replica count
 * @param message        human readable status message
 * @param timestamp      operation time
 */
public record ScaleResponse(
        String namespace,
        String deploymentName,
        ScaleDirection direction,
        int replicas,
        String message,
        OffsetDateTime timestamp) {

    public static ScaleResponse success(String namespace, String deploymentName,
                                         ScaleDirection direction, int replicas) {
        return new ScaleResponse(namespace, deploymentName, direction, replicas,
                "Deployment scaled successfully", OffsetDateTime.now());
    }
}