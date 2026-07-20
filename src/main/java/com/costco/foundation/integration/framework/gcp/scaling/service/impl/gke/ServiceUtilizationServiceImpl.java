package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.MetricDataPoint;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.MetricSeries;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceUtilizationResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ServiceUtilizationMetric;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationMetric;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationTimeRange;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceUtilizationService;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
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
 * Default implementation that reads container-level CPU, Memory, and Disk
 * metrics for a specific service (identified by namespace + container name) from
 * the Cloud Monitoring API and shapes them into per-metric time series suitable
 * for streaming line charts.
 */
@Service
public class ServiceUtilizationServiceImpl implements ServiceUtilizationService {

    private static final Logger _log = LoggerFactory.getLogger(ServiceUtilizationServiceImpl.class);

    /** Monitored resource type for GKE containers. */
    private static final String RESOURCE_TYPE = "k8s_container";

    /** Lookback used when fetching the latest single sample for streaming. */
    private static final Duration LATEST_LOOKBACK = Duration.ofMinutes(5);

    /** Units surfaced to the client per metric category. */
    private static final String UNIT_FRACTION = "ratio";
    private static final String UNIT_BYTES = "bytes";

    /**
     * Monitoring filter template.
     * Params: metric type, container resource type, cluster, namespace, container.
     */
    private static final String FILTER_TEMPLATE = """
            metric.type="%s" \
            AND resource.type="%s" \
            AND resource.labels.cluster_name="%s" \
            AND resource.labels.namespace_name="%s" \
            AND resource.labels.container_name="%s"\
            """;

    private final MetricServiceClient _metricServiceClient;

    public ServiceUtilizationServiceImpl(MetricServiceClient metricServiceClient) {
        _metricServiceClient = metricServiceClient;
    }

    @Override
    public ServiceUtilizationResponse getUtilization(
            String projectId, String clusterId, String namespace,
            String serviceName, UtilizationTimeRange range) {
        var effectiveRange = range != null ? range : UtilizationTimeRange.DEFAULT;
        if (effectiveRange == UtilizationTimeRange.CUSTOM) {
            throw new ScalingException("CUSTOM range requires explicit startTime and endTime");
        }
        var end = OffsetDateTime.now(ZoneOffset.UTC);
        var start = end.minus(effectiveRange.duration());
        return buildResponse(projectId, clusterId, namespace, serviceName, start, end);
    }

    @Override
    public ServiceUtilizationResponse getUtilization(
            String projectId, String clusterId, String namespace, String serviceName,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new ScalingException(
                    "Custom range requires a valid startTime strictly before endTime");
        }
        return buildResponse(projectId, clusterId, namespace, serviceName, startTime, endTime);
    }

    @Override
    public ServiceUtilizationResponse getLatest(
            String projectId, String clusterId, String namespace, String serviceName) {
        var end = OffsetDateTime.now(ZoneOffset.UTC);
        var start = end.minus(LATEST_LOOKBACK);
        var full = buildResponse(projectId, clusterId, namespace, serviceName, start, end);

        var latestSeries = full.series().stream()
                .map(this::keepLatestPoint)
                .toList();
        return new ServiceUtilizationResponse(
                projectId, clusterId, namespace, serviceName, start, end, latestSeries);
    }

    /**
     * Fetches all utilization metrics for the window and assembles the response.
     */
    private ServiceUtilizationResponse buildResponse(
            String projectId, String clusterId, String namespace,
            String serviceName, OffsetDateTime start, OffsetDateTime end) {
        _log.info("Fetching utilization for service '{}' in namespace '{}', cluster '{}' [{} .. {}]",
                serviceName, namespace, clusterId, start, end);
        try {
            var interval = toInterval(start, end);
            var series = new ArrayList<MetricSeries>();
            for (var metric : ServiceUtilizationMetric.values()) {
                series.add(fetchSeries(projectId, clusterId, namespace, serviceName, metric, interval));
            }
            return new ServiceUtilizationResponse(
                    projectId, clusterId, namespace, serviceName, start, end, series);
        } catch (Exception ex) {
            _log.error("Failed to fetch utilization for service '{}' in namespace '{}', cluster '{}'",
                    serviceName, namespace, clusterId, ex);
            throw new ScalingException(
                    "Failed to fetch utilization for service: " + serviceName, ex);
        }
    }

    /**
     * Queries a single container metric and aggregates points (averaged per
     * timestamp across replica pods) into one service-level series.
     */
    private MetricSeries fetchSeries(
            String projectId, String clusterId, String namespace, String serviceName,
            ServiceUtilizationMetric metric, TimeInterval interval) {
        var filter = FILTER_TEMPLATE.formatted(
                metric.metricType(), RESOURCE_TYPE, clusterId, namespace, serviceName);

        var request = ListTimeSeriesRequest.newBuilder()
                .setName(ProjectName.of(projectId).toString())
                .setFilter(filter)
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .build();

        var points = new ArrayList<MetricDataPoint>();
        for (var timeSeries : _metricServiceClient.listTimeSeries(request).iterateAll()) {
            for (var point : timeSeries.getPointsList()) {
                points.add(toDataPoint(point));
            }
        }
        points.sort(Comparator.comparing(MetricDataPoint::timestamp));

        // Map container metric category to the shared MetricSeries metric enum.
        return new MetricSeries(toSharedMetric(metric), unitFor(metric), points);
    }

    /**
     * Extracts a value from a Monitoring point, handling both double-valued
     * (utilization ratios) and int64-valued (byte counts) metrics.
     */
    private MetricDataPoint toDataPoint(Point point) {
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

    private String unitFor(ServiceUtilizationMetric metric) {
        return switch (metric) {
            case CPU, MEMORY -> UNIT_FRACTION;
            case DISK -> UNIT_BYTES;
        };
    }

    /**
     * Maps the container-specific metric category to the shared
     * {@link UtilizationMetric} used by {@link MetricSeries}, so the frontend
     * consumes a consistent metric label across cluster and service widgets.
     */
    private UtilizationMetric toSharedMetric(ServiceUtilizationMetric metric) {
        return switch (metric) {
            case CPU -> UtilizationMetric.CPU;
            case MEMORY -> UtilizationMetric.MEMORY;
            case DISK -> UtilizationMetric.DISK;
        };
    }

    private TimeInterval toInterval(OffsetDateTime start, OffsetDateTime end) {
        return TimeInterval.newBuilder()
                .setStartTime(Timestamps.fromMillis(start.toInstant().toEpochMilli()))
                .setEndTime(Timestamps.fromMillis(end.toInstant().toEpochMilli()))
                .build();
    }
}