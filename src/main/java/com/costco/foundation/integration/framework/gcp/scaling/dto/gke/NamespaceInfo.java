package com.costco.foundation.integration.framework.gcp.scaling.dto.gke;

/**
 * Represents a single user-created Kubernetes namespace.
 *
 * @param name   the namespace name
 * @param status the namespace lifecycle phase (e.g. {@code Active}), may be {@code null}
 */
public record NamespaceInfo(String name, String status) {
}