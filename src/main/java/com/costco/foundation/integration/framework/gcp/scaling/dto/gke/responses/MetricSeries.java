package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationMetric;

import java.util.List;

/**
 * A single metric series (e.g. CPU) for the requested window.
 *
 * @param metric the utilization category
 * @param unit   human-readable unit of {@link MetricDataPoint#value()}
 * @param points the ordered samples, oldest first
 */
public record MetricSeries(
        UtilizationMetric metric,
        String unit,
        List<MetricDataPoint> points) {
}