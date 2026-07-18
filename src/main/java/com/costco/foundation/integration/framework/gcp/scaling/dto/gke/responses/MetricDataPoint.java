package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.time.OffsetDateTime;

/**
 * A single time-stamped utilization sample.
 *
 * @param timestamp the sample time (UTC)
 * @param value     the utilization value (fraction 0..1 for CPU/MEMORY, bytes for DISK)
 */
public record MetricDataPoint(OffsetDateTime timestamp, double value) {
}