package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ScaleDirection;

import java.time.OffsetDateTime;

/**
 * Standard response after a scale-to-replicas operation.
 *
 * @param projectId        GKE project identifier
 * @param namespace        target namespace
 * @param serviceName      target service/deployment name
 * @param direction        scaling direction performed (null when no change)
 * @param previousReplicas replica count before the operation
 * @param desiredReplicas  resulting replica count
 * @param minPods          HPA minimum replicas
 * @param maxPods          HPA maximum replicas
 * @param changed          whether the replica count actually changed
 * @param message          human readable status message
 * @param timestamp        operation time
 */
public record ScaleToReplicasResponse(
        String projectId,
        String namespace,
        String serviceName,
        ScaleDirection direction,
        int previousReplicas,
        int desiredReplicas,
        int minPods,
        int maxPods,
        boolean changed,
        String message,
        OffsetDateTime timestamp) {

    public static ScaleToReplicasResponse scaled(String projectId, String namespace, String serviceName,
                                                 ScaleDirection direction, int previousReplicas,
                                                 int desiredReplicas, int minPods, int maxPods) {
        var message = "Service scaled %s from %d to %d replicas"
                .formatted(direction, previousReplicas, desiredReplicas);
        return new ScaleToReplicasResponse(projectId, namespace, serviceName, direction,
                previousReplicas, desiredReplicas, minPods, maxPods, true, message, OffsetDateTime.now());
    }

    public static ScaleToReplicasResponse noChange(String projectId, String namespace, String serviceName,
                                                  int replicas, int minPods, int maxPods) {
        var message = "Service already running at desired replica count %d".formatted(replicas);
        return new ScaleToReplicasResponse(projectId, namespace, serviceName, null,
                replicas, replicas, minPods, maxPods, false, message, OffsetDateTime.now());
    }
}