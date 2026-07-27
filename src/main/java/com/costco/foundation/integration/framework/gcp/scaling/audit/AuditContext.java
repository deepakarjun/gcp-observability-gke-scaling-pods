package com.costco.foundation.integration.framework.gcp.scaling.audit;

/**
 * Explicit context for an audited operation, supplied by the annotated method so
 * the aspect never has to infer values from parameter names or DTO reflection.
 *
 * @param projectId   the GCP project id (required)
 * @param clusterName the cluster name (required)
 * @param namespace   the namespace (nullable for cluster-scoped actions)
 * @param serviceName the service name (nullable for cluster-scoped actions)
 */
public record AuditContext(
        String projectId,
        String clusterName,
        String namespace,
        String serviceName) {

    /**
     * Factory for a cluster-scoped context (no namespace/service).
     *
     * @param projectId   the GCP project id
     * @param clusterName the cluster name
     * @return a cluster-scoped {@link AuditContext}
     */
    public static AuditContext ofCluster(String projectId, String clusterName) {
        return new AuditContext(projectId, clusterName, null, null);
    }

    /**
     * Factory for a service-scoped context.
     *
     * @param projectId   the GCP project id
     * @param clusterName the cluster name
     * @param namespace   the namespace
     * @param serviceName the service name
     * @return a service-scoped {@link AuditContext}
     */
    public static AuditContext ofService(
            String projectId, String clusterName, String namespace, String serviceName) {
        return new AuditContext(projectId, clusterName, namespace, serviceName);
    }
}