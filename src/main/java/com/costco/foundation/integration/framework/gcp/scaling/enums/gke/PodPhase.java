package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

/**
 * Kubernetes pod lifecycle phases relevant to key-point aggregation.
 */
public enum PodPhase {

    RUNNING("Running"),
    PENDING("Pending"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    UNKNOWN("Unknown");

    private final String _value;

    PodPhase(String value) {
        _value = value;
    }

    /**
     * @return the exact phase string reported by the Kubernetes API
     */
    public String value() {
        return _value;
    }
}