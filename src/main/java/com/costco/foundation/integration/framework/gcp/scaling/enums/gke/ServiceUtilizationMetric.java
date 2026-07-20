package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

/**
 * Container-level utilization metric categories for a running service.
 */
public enum ServiceUtilizationMetric {

    /** Container CPU usage as a fraction of its request (0..1). */
    CPU("kubernetes.io/container/cpu/request_utilization"),

    /** Container memory usage as a fraction of its request (0..1). */
    MEMORY("kubernetes.io/container/memory/request_utilization"),

    /** Container ephemeral storage (disk) used bytes. */
    DISK("kubernetes.io/container/ephemeral_storage/used_bytes");

    private final String _metricType;

    ServiceUtilizationMetric(String metricType) {
        _metricType = metricType;
    }

    /** @return the Cloud Monitoring metric type backing this category */
    public String metricType() {
        return _metricType;
    }
}