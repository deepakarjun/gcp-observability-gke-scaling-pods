package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the log-severity KPI metric source.
 *
 * @param metricType the Cloud Monitoring metric type to read severity counts from
 */
@ConfigurationProperties(prefix = "gke.log-severity")
public record LogSeverityMetricProperties(String metricType) {

    /** Falls back to the built-in system metric when unset. */
    public LogSeverityMetricProperties {
        metricType = (metricType != null && !metricType.isBlank())
                ? metricType
                : "logging.googleapis.com/log_entry_count";
    }
}