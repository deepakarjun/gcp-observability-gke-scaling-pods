package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityMetricProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.LogSeverityCounts;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/**
 * Default implementation that derives per-severity counts for a GKE cluster's
 * user-namespace workloads from the built-in Cloud Monitoring metric
 * {@code logging.googleapis.com/log_entry_count}.
 *
 * <p>This reads a single, pre-aggregated time series instead of paging through
 * raw log entries, which avoids the Cloud Logging "Read requests per minute per
 * user" quota entirely and returns results in one API call.</p>
 */
@Service
public class LogSeverityServiceImpl implements LogSeverityService {

    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);

    /** Rolling window over which severities are counted. */
//    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(7);
    private static final Duration LOOKBACK_WINDOW = Duration.ofMinutes(10);

    /** Built-in log-based metric exposing entry counts by severity. */
//    private static final String LOG_ENTRY_COUNT_METRIC = "logging.googleapis.com/log_entry_count";

    /** Monitored resource type for GKE workload (container) logs. */
    private static final String RESOURCE_TYPE = "k8s_container";

    /** Metric label carrying the log severity. */
    private static final String SEVERITY_LABEL = "severity";

    /** Resource label carrying the namespace name. */
    private static final String NAMESPACE_LABEL = "namespace_name";

    /** Empty result reused when no user namespaces are supplied. */
    private static final LogSeverityCounts EMPTY_COUNTS =
            new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);

    /**
     * Monitoring filter template.
     * Params: metric type, resource type, cluster name.
     */
    private static final String FILTER_TEMPLATE = """
            metric.type="%s" \
            AND resource.type="%s" \
            AND resource.labels.cluster_name="%s"\
            """;

    private final MetricServiceClient _metricServiceClient;
    private final LogSeverityMetricProperties _metricProperties;

    public LogSeverityServiceImpl(MetricServiceClient metricServiceClient, LogSeverityMetricProperties metricProperties) {
        _metricServiceClient = metricServiceClient;
        _metricProperties = metricProperties;
    }

    @Override
    public LogSeverityCounts getWeeklySeverityCounts(
            String projectId, String clusterId, List<String> userNamespaces) {
        if (userNamespaces == null || userNamespaces.isEmpty()) {
            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
                    clusterId);
            return EMPTY_COUNTS;
        }

        _log.info("Reading weekly severity metric for cluster '{}' across {} namespace(s)",
                clusterId, userNamespaces.size());
        try {
            var userNamespaceSet = Set.copyOf(userNamespaces);
            var request = buildRequest(projectId, clusterId);
            var totals = newSeverityMap();

            for (var series : _metricServiceClient.listTimeSeries(request).iterateAll()) {
                accumulate(series, userNamespaceSet, totals);
            }

            var counts = toCounts(totals);
            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
            return counts;
        } catch (Exception ex) {
            _log.error("Failed to read severity metric for cluster '{}' in project '{}'",
                    clusterId, projectId, ex);
            throw new ScalingException(
                    "Failed to read severity metric for cluster: " + clusterId, ex);
        }
    }

    /**
     * Builds a single {@link ListTimeSeriesRequest} for the log-entry-count metric
     * scoped to the cluster over the lookback window.
     */
    
    private ListTimeSeriesRequest buildRequest(String projectId, String clusterId) {
        var now = Instant.now();
        var interval = TimeInterval.newBuilder()
                .setStartTime(Timestamps.fromMillis(now.minus(LOOKBACK_WINDOW).toEpochMilli()))
                .setEndTime(Timestamps.fromMillis(now.toEpochMilli()))
                .build();

        // Metric type is externalized so the KPI can point at a noise-filtered,
        // user-defined log-based metric instead of the raw system metric.
        var filter = FILTER_TEMPLATE.formatted(
                _metricProperties.metricType(), RESOURCE_TYPE, clusterId);

        return ListTimeSeriesRequest.newBuilder()
                .setName(ProjectName.of(projectId).toString())
                .setFilter(filter)
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .build();
    }
    
//    private ListTimeSeriesRequest buildRequest(String projectId, String clusterId) {
//        var now = Instant.now();
//        var interval = TimeInterval.newBuilder()
//                .setStartTime(Timestamps.fromMillis(now.minus(LOOKBACK_WINDOW).toEpochMilli()))
//                .setEndTime(Timestamps.fromMillis(now.toEpochMilli()))
//                .build();
//
//        var filter = FILTER_TEMPLATE.formatted(
//                LOG_ENTRY_COUNT_METRIC, RESOURCE_TYPE, clusterId);
//
//        return ListTimeSeriesRequest.newBuilder()
//                .setName(ProjectName.of(projectId).toString())
//                .setFilter(filter)
//                .setInterval(interval)
//                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
//                .build();
//    }

    /**
     * Adds a single time series' points to the running totals, but only for the
     * supplied user namespaces and tracked severities.
     */
    private void accumulate(
            TimeSeries series, Set<String> userNamespaces, Map<LogSeverity, Long> totals) {
        var namespace = series.getResource().getLabelsMap().get(NAMESPACE_LABEL);
        if (namespace == null || !userNamespaces.contains(namespace)) {
            return; // exclude system/control-plane namespaces
        }

        var severityLabel = series.getMetric().getLabelsMap().get(SEVERITY_LABEL);
        mapSeverity(severityLabel).ifPresent(severity -> {
            var sum = series.getPointsList().stream()
                    .mapToLong(point -> point.getValue().getInt64Value())
                    .sum();
            totals.merge(severity, sum, Long::sum);
        });
    }

    /** Maps a Cloud Logging severity label to a tracked {@link LogSeverity}, if applicable. */
    private Optional<LogSeverity> mapSeverity(String severityLabel) {
        if (severityLabel == null) {
            return Optional.empty();
        }
        return switch (severityLabel) {
            case "WARNING" -> Optional.of(LogSeverity.WARNING);
            case "ERROR" -> Optional.of(LogSeverity.ERROR);
            case "CRITICAL" -> Optional.of(LogSeverity.CRITICAL);
            case "ALERT" -> Optional.of(LogSeverity.ALERT);
            case "EMERGENCY" -> Optional.of(LogSeverity.EMERGENCY);
            default -> Optional.empty();
        };
    }

    private Map<LogSeverity, Long> newSeverityMap() {
        var map = new EnumMap<LogSeverity, Long>(LogSeverity.class);
        for (var severity : LogSeverity.values()) {
            map.put(severity, 0L);
        }
        return map;
    }

    private LogSeverityCounts toCounts(Map<LogSeverity, Long> totals) {
        return new LogSeverityCounts(
                totals.get(LogSeverity.WARNING),
                totals.get(LogSeverity.ERROR),
                totals.get(LogSeverity.CRITICAL),
                totals.get(LogSeverity.ALERT),
                totals.get(LogSeverity.EMERGENCY));
    }
}
