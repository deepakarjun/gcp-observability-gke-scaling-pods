package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.util.List;

/**
 * Version history for a single service (Deployment) within a namespace.
 *
 * @param projectId   the GCP project
 * @param clusterName the cluster
 * @param namespace   the namespace containing the service
 * @param serviceName the Deployment name
 * @param totalVersions number of revisions found
 * @param versions    the revision history, newest first
 */
public record ServiceVersionHistory(
        String projectId,
        String clusterName,
        String namespace,
        String serviceName,
        int totalVersions,
        List<ServiceVersion> versions) {
}