package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retry/backoff settings for Cloud Logging severity queries.
 *
 * @param maxRetries          maximum retry attempts on quota exhaustion
 * @param initialBackoffMillis initial backoff delay in milliseconds
 * @param backoffMultiplier    multiplier applied to the delay after each retry
 */

@ConfigurationProperties(prefix = "gke.log-severity")
//@ConstructorBinding
public record LogSeverityRetryProperties(
        int maxRetries,
        long initialBackoffMillis,
        int backoffMultiplier) {

    /** Fallbacks if properties are absent. */
    public LogSeverityRetryProperties {
        maxRetries = maxRetries > 0 ? maxRetries : 4;
        initialBackoffMillis = initialBackoffMillis > 0 ? initialBackoffMillis : 1000L;
        backoffMultiplier = backoffMultiplier > 0 ? backoffMultiplier : 2;
    }
}