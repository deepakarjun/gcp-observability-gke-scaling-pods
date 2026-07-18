package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Streaming settings for the utilization SSE endpoint.
 *
 * @param streamIntervalMillis push cadence for live samples
 * @param streamTimeoutMillis  SSE connection timeout (0 = none)
 */
@ConfigurationProperties(prefix = "gke.utilization")
public record UtilizationStreamProperties(
        long streamIntervalMillis,
        long streamTimeoutMillis) {

    /** Sensible defaults if unset. */
    public UtilizationStreamProperties {
        streamIntervalMillis = streamIntervalMillis > 0 ? streamIntervalMillis : 15000L;
        streamTimeoutMillis = streamTimeoutMillis >= 0 ? streamTimeoutMillis : 0L;
    }
}