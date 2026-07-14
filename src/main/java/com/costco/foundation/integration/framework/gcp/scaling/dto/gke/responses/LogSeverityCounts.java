package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

/**
 * Cloud Logging severity counts for a cluster over a rolling window.
 *
 * @param warnings   number of {@code WARNING} entries
 * @param errors     number of {@code ERROR} entries
 * @param criticals  number of {@code CRITICAL} entries
 * @param alerts     number of {@code ALERT} entries
 * @param emergencies number of {@code EMERGENCY} entries
 */
public record LogSeverityCounts(
        long warnings,
        long errors,
        long criticals,
        long alerts,
        long emergencies) {
}