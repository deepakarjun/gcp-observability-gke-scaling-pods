package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

/**
 * Cloud Logging severity levels tracked for the cluster key-points summary.
 * Each maps to the exact severity token used in Cloud Logging filters.
 */
public enum LogSeverity {

    WARNING("WARNING"),
    ERROR("ERROR"),
    CRITICAL("CRITICAL"),
    ALERT("ALERT"),
    EMERGENCY("EMERGENCY");

    private final String _value;

    LogSeverity(String value) {
        _value = value;
    }

    /**
     * @return the severity token used in Cloud Logging filter expressions
     */
    public String value() {
        return _value;
    }
}