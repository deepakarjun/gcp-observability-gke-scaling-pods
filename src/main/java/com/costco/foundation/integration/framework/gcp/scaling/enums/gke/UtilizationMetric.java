package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

/**
 * Cluster utilization metric categories exposed by the streaming widgets.
 */
public enum UtilizationMetric {

    /** Node CPU allocatable utilization (0..1). */
    CPU("kubernetes.io/node/cpu/allocatable_utilization"),

    /** Node memory allocatable utilization (0..1). */
    MEMORY("kubernetes.io/node/memory/allocatable_utilization"),

    /** Node ephemeral storage (disk) used bytes. */
    DISK("kubernetes.io/node/ephemeral_storage/used_bytes");

    private final String _metricType;

    UtilizationMetric(String metricType) {
        _metricType = metricType;
    }

    /** @return the Cloud Monitoring metric type backing this category */
    public String metricType() {
        return _metricType;
    }
}