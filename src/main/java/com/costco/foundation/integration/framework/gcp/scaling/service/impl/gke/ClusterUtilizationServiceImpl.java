package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterUtilizationResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.MetricDataPoint;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.MetricSeries;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationMetric;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationTimeRange;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterUtilizationService;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation that reads node-level CPU, Memory, and Disk metrics for
 * a GKE cluster from the Cloud Monitoring API and shapes them into per-metric
 * time series suitable for streaming line charts.
 */
@Service
public class ClusterUtilizationServiceImpl implements ClusterUtilizationService {

    private static final Logger _log = LoggerFactory.getLogger(ClusterUtilizationServiceImpl.class);

    /** Monitored resource type for GKE nodes. */
    private static final String RESOURCE_TYPE = "k8s_node";

    /** Lookback used when fetching the latest single sample for streaming. */
    private static final Duration LATEST_LOOKBACK = Duration.ofMinutes(5);

    /** Units surfaced to the client per metric category. */
    private static final String UNIT_FRACTION = "ratio";
    private static final String UNIT_BYTES = "bytes";

    /**
     * Monitoring filter template.
     * Params: metric type, node resource type, cluster name.
     */
    private static final String FILTER_TEMPLATE = """
            metric.type="%s" \
            AND resource.type="%s" \
            AND resource.labels.cluster_name="%s"\
            """;

    private final MetricServiceClient _metricServiceClient;

    public ClusterUtilizationServiceImpl(MetricServiceClient metricServiceClient) {
        _metricServiceClient = metricServiceClient;
    }

    @Override
    public ClusterUtilizationResponse getUtilization(
            String projectId, String clusterId, UtilizationTimeRange range) {
        var effectiveRange = range != null ? range : UtilizationTimeRange.DEFAULT;
        if (effectiveRange == UtilizationTimeRange.CUSTOM) {
            throw new ScalingException(
                    "CUSTOM range requires explicit startTime and endTime");
        }
        var end = OffsetDateTime.now(ZoneOffset.UTC);
        var start = end.minus(effectiveRange.duration());
        return buildResponse(projectId, clusterId, start, end);
    }

    @Override
    public ClusterUtilizationResponse getUtilization(
            String projectId, String clusterId,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new ScalingException(
                    "Custom range requires a valid startTime strictly before endTime");
        }
        return buildResponse(projectId, clusterId, startTime, endTime);
    }

    @Override
    public ClusterUtilizationResponse getLatest(String projectId, String clusterId) {
        var end = OffsetDateTime.now(ZoneOffset.UTC);
        var start = end.minus(LATEST_LOOKBACK);
        var full = buildResponse(projectId, clusterId, start, end);

        // Keep only the most recent point per series for a lightweight push.
        var latestSeries = full.series().stream()
                .map(this::keepLatestPoint)
                .toList();
        return new ClusterUtilizationResponse(
                projectId, clusterId, start, end, latestSeries);
    }

    /**
     * Fetches all utilization metrics for the window and assembles the response.
     */
    private ClusterUtilizationResponse buildResponse(
            String projectId, String clusterId, OffsetDateTime start, OffsetDateTime end) {
        _log.info("Fetching utilization for cluster '{}' [{} .. {}]", clusterId, start, end);
        try {
            var interval = toInterval(start, end);
            var series = new ArrayList<MetricSeries>();
            for (var metric : UtilizationMetric.values()) {
                series.add(fetchSeries(projectId, clusterId, metric, interval));
            }
            return new ClusterUtilizationResponse(projectId, clusterId, start, end, series);
        } catch (Exception ex) {
            _log.error("Failed to fetch utilization for cluster '{}' in project '{}'",
                    clusterId, projectId, ex);
            throw new ScalingException(
                    "Failed to fetch utilization for cluster: " + clusterId, ex);
        }
    }

    /**
     * Queries a single metric and aggregates node-level points into a
     * cluster-level series (averaged per timestamp across nodes).
     */
    private MetricSeries fetchSeries(
            String projectId, String clusterId,
            UtilizationMetric metric, TimeInterval interval) {
        var filter = FILTER_TEMPLATE.formatted(
                metric.metricType(), RESOURCE_TYPE, clusterId);

        var request = ListTimeSeriesRequest.newBuilder()
                .setName(ProjectName.of(projectId).toString())
                .setFilter(filter)
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .build();

        var points = new ArrayList<MetricDataPoint>();
        for (var timeSeries : _metricServiceClient.listTimeSeries(request).iterateAll()) {
            for (var point : timeSeries.getPointsList()) {
                points.add(toDataPoint(point, timeSeries));
            }
        }
        points.sort(Comparator.comparing(MetricDataPoint::timestamp));

        return new MetricSeries(metric, unitFor(metric), points);
    }

    /**
     * Extracts a value from a Monitoring point, handling both double-valued
     * (utilization ratios) and int64-valued (byte counts) metrics.
     */
    private MetricDataPoint toDataPoint(Point point, TimeSeries series) {
        var typedValue = point.getValue();
        var value = switch (typedValue.getValueCase()) {
            case DOUBLE_VALUE -> typedValue.getDoubleValue();
            case INT64_VALUE -> (double) typedValue.getInt64Value();
            default -> 0.0d;
        };
        var epochMillis = Timestamps.toMillis(point.getInterval().getEndTime());
        var timestamp = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
        return new MetricDataPoint(timestamp, value);
    }

    private MetricSeries keepLatestPoint(MetricSeries series) {
        var points = series.points();
        var latest = points.isEmpty()
                ? List.<MetricDataPoint>of()
                : List.of(points.get(points.size() - 1));
        return new MetricSeries(series.metric(), series.unit(), latest);
    }

    private String unitFor(UtilizationMetric metric) {
        return switch (metric) {
            case CPU, MEMORY -> UNIT_FRACTION;
            case DISK -> UNIT_BYTES;
        };
    }

    private TimeInterval toInterval(OffsetDateTime start, OffsetDateTime end) {
        return TimeInterval.newBuilder()
                .setStartTime(Timestamps.fromMillis(start.toInstant().toEpochMilli()))
                .setEndTime(Timestamps.fromMillis(end.toInstant().toEpochMilli()))
                .build();
    }
}