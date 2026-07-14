package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;

/**
 * Result of a single concurrent severity-count task.
 *
 * @param severity the severity that was counted
 * @param count    the number of matching log entries
 */
public record SeverityCountResult(LogSeverity severity, long count) {
}