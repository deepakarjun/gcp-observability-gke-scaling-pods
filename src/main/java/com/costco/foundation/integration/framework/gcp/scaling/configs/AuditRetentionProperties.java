package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention settings for the audit log purge job.
 *
 * @param retentionDays entries older than this many days are purged
 * @param purgeCron     cron expression controlling the purge schedule
 */
@ConfigurationProperties(prefix = "audit.retention")
public record AuditRetentionProperties(
        long retentionDays,
        String purgeCron) {

    /** Default retention window applied when unset or invalid. */
    private static final long DEFAULT_RETENTION_DAYS = 90L;

    /** Default purge schedule: daily at 02:00. */
    private static final String DEFAULT_PURGE_CRON = "0 0 2 * * *";

    /** Applies sensible defaults for missing or invalid values. */
    public AuditRetentionProperties {
        retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
        purgeCron = (purgeCron != null && !purgeCron.isBlank())
                ? purgeCron : DEFAULT_PURGE_CRON;
    }
}