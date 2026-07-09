package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

import java.util.List;

/**
 * Response for the "list user namespaces in a cluster" endpoint.
 *
 * @param projectId      the GCP project
 * @param clusterName    the cluster the namespaces belong to
 * @param namespaceCount number of user namespaces returned
 * @param namespaces     the user-created namespaces (system namespaces excluded)
 */
public record NamespaceListResponse(
        String projectId,
        String clusterName,
        int namespaceCount,
        List<NamespaceInfo> namespaces) {
}