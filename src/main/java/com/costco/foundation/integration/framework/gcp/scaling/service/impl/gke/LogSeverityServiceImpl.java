package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
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
    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(7);

    /** Built-in log-based metric exposing entry counts by severity. */
    private static final String LOG_ENTRY_COUNT_METRIC = "logging.googleapis.com/log_entry_count";

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

    public LogSeverityServiceImpl(MetricServiceClient metricServiceClient) {
        _metricServiceClient = metricServiceClient;
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

        var filter = FILTER_TEMPLATE.formatted(
                LOG_ENTRY_COUNT_METRIC, RESOURCE_TYPE, clusterId);

        return ListTimeSeriesRequest.newBuilder()
                .setName(ProjectName.of(projectId).toString())
                .setFilter(filter)
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .build();
    }

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

//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityExecutorConfig;
//import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityRetryProperties;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
//import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
//import com.google.cloud.logging.Logging;
//import com.google.cloud.logging.Logging.EntryListOption;
//import com.google.cloud.logging.LoggingException;
//import com.google.cloud.logging.Severity;
//import io.grpc.Status;
//import io.grpc.StatusRuntimeException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//import java.util.EnumMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//
///**
// * Default implementation that queries Cloud Logging for per-severity entry
// * counts scoped to a GKE cluster's <em>user-namespace</em> workloads (services)
// * over a rolling one-week window.
// *
// * <p>To reduce both latency and API pressure, each user namespace is queried
// * <strong>once</strong> using a {@code severity>=WARNING} filter, and the five
// * tracked severities are tallied in memory. Namespaces are processed
// * concurrently on a bounded {@link ExecutorService}, and each query is retried
// * with exponential backoff when Cloud Logging returns
// * {@code RESOURCE_EXHAUSTED}.</p>
// */
//@Service
//public class LogSeverityServiceImpl implements LogSeverityService {
//
//    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);
//
//    /** Rolling window over which severities are counted. */
//    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(7);
//
//    /** GKE container log resource type (i.e. service/workload container logs). */
//    private static final String RESOURCE_TYPE = "k8s_container";
//
//    /** Page size used when paging through matching log entries. */
//    private static final int PAGE_SIZE = 1000;
//
//    /** Lowest severity included by the single combined query. */
//    private static final String MIN_SEVERITY = "WARNING";
//
//    /** Empty result reused when no user namespaces are supplied. */
//    private static final LogSeverityCounts EMPTY_COUNTS =
//            new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);
//
//    /**
//     * Single combined filter per namespace (counts all tracked severities at once).
//     * Params: resource type, cluster name, namespace, min severity, RFC3339 start.
//     */
//    private static final String FILTER_TEMPLATE = """
//            resource.type="%s"
//            AND resource.labels.cluster_name="%s"
//            AND resource.labels.namespace_name="%s"
//            AND severity>=%s
//            AND timestamp>="%s"
//            """;
//
//    private final Logging _logging;
//    private final ExecutorService _executor;
//    private final LogSeverityRetryProperties _retryProperties;
//
//    public LogSeverityServiceImpl(
//            Logging logging,
//            @Qualifier(LogSeverityExecutorConfig.LOG_SEVERITY_EXECUTOR_BEAN) ExecutorService executor,
//            LogSeverityRetryProperties retryProperties) {
//        _logging = logging;
//        _executor = executor;
//        _retryProperties = retryProperties;
//    }
//
//    @Override
//    public LogSeverityCounts getWeeklySeverityCounts(
//            String projectId, String clusterId, List<String> userNamespaces) {
//        if (userNamespaces == null || userNamespaces.isEmpty()) {
//            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
//                    clusterId);
//            return EMPTY_COUNTS;
//        }
//
//        _log.info("Counting weekly service log severities for cluster '{}' across {} namespace(s) "
//                + "(one query per namespace, bounded concurrency)", clusterId, userNamespaces.size());
//        try {
//            var startTimestamp = DateTimeFormatter.ISO_INSTANT
//                    .format(Instant.now().minus(LOOKBACK_WINDOW));
//
//            // One future per namespace; each returns that namespace's per-severity tally.
//            var futures = userNamespaces.stream()
//                    .map(namespace -> CompletableFuture.supplyAsync(
//                            () -> countNamespaceSeverities(clusterId, namespace, startTimestamp),
//                            _executor))
//                    .toList();
//
//            var totals = newSeverityMap();
//            futures.forEach(future -> mergeInto(totals, future.join()));
//
//            var counts = toCounts(totals);
//            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
//            return counts;
//        } catch (Exception ex) {
//            _log.error("Failed to count service log severities for cluster '{}' in project '{}'",
//                    clusterId, projectId, ex);
//            throw new ScalingException(
//                    "Failed to count service log severities for cluster: " + clusterId, ex);
//        }
//    }
//
//    /**
//     * Runs a single {@code severity>=WARNING} query for one namespace and tallies
//     * each tracked {@link LogSeverity} in memory. Retries with exponential backoff
//     * on quota exhaustion.
//     */
//    private Map<LogSeverity, Long> countNamespaceSeverities(
//            String clusterId, String namespace, String startTimestamp) {
//        var filter = FILTER_TEMPLATE.formatted(
//                RESOURCE_TYPE, clusterId, namespace, MIN_SEVERITY, startTimestamp);
//
//        var tally = newSeverityMap();
//        var entries = listWithRetry(filter, clusterId, namespace);
//
//        for (var entry : entries) {
//            mapSeverity(entry.getSeverity()).ifPresent(
//                    severity -> tally.merge(severity, 1L, Long::sum));
//        }
//        _log.debug("Cluster '{}' namespace '{}' severity tally = {}", clusterId, namespace, tally);
//        return tally;
//    }
//
//    /**
//     * Executes the Logging list call, retrying on {@code RESOURCE_EXHAUSTED}
//     * using exponential backoff configured via {@link LogSeverityRetryProperties}.
//     */
//    private Iterable<com.google.cloud.logging.LogEntry> listWithRetry(
//            String filter, String clusterId, String namespace) {
//        var attempt = 0;
//        var backoffMillis = _retryProperties.initialBackoffMillis();
//
//        while (true) {
//            try {
//                return _logging.listLogEntries(
//                        EntryListOption.filter(filter),
//                        EntryListOption.pageSize(PAGE_SIZE)).iterateAll();
//            } catch (LoggingException ex) {
//                if (!isQuotaExhausted(ex) || attempt >= _retryProperties.maxRetries()) {
//                    throw ex;
//                }
//                attempt++;
//                _log.warn("Quota exhausted for cluster '{}' namespace '{}'; retry {}/{} after {} ms",
//                        clusterId, namespace, attempt, _retryProperties.maxRetries(), backoffMillis);
//                sleep(backoffMillis);
//                backoffMillis *= _retryProperties.backoffMultiplier();
//            }
//        }
//    }
//
//    /**
//     * @return {@code true} if the exception is a Cloud Logging RESOURCE_EXHAUSTED quota error
//     */
//    private boolean isQuotaExhausted(LoggingException ex) {
//        return ex.getCause() instanceof StatusRuntimeException sre
//                && sre.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
//    }
//
//    private void sleep(long millis) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException ie) {
//            Thread.currentThread().interrupt();
//            throw new ScalingException("Interrupted while backing off on Logging quota", ie);
//        }
//    }
//
//    /** Maps a Cloud Logging {@link Severity} to a tracked {@link LogSeverity}, if applicable. */
//    private java.util.Optional<LogSeverity> mapSeverity(Severity severity) {
//        return switch (severity) {
//            case WARNING -> java.util.Optional.of(LogSeverity.WARNING);
//            case ERROR -> java.util.Optional.of(LogSeverity.ERROR);
//            case CRITICAL -> java.util.Optional.of(LogSeverity.CRITICAL);
//            case ALERT -> java.util.Optional.of(LogSeverity.ALERT);
//            case EMERGENCY -> java.util.Optional.of(LogSeverity.EMERGENCY);
//            default -> java.util.Optional.empty();
//        };
//    }
//
//    private Map<LogSeverity, Long> newSeverityMap() {
//        var map = new EnumMap<LogSeverity, Long>(LogSeverity.class);
//        for (var severity : LogSeverity.values()) {
//            map.put(severity, 0L);
//        }
//        return map;
//    }
//
//    private void mergeInto(Map<LogSeverity, Long> target, Map<LogSeverity, Long> source) {
//        source.forEach((severity, count) -> target.merge(severity, count, Long::sum));
//    }
//
//    private LogSeverityCounts toCounts(Map<LogSeverity, Long> totals) {
//        return new LogSeverityCounts(
//                totals.get(LogSeverity.WARNING),
//                totals.get(LogSeverity.ERROR),
//                totals.get(LogSeverity.CRITICAL),
//                totals.get(LogSeverity.ALERT),
//                totals.get(LogSeverity.EMERGENCY));
//    }
//}
//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityExecutorConfig;
//import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityRetryProperties;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
//import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
//import com.google.cloud.logging.Logging;
//import com.google.cloud.logging.Logging.EntryListOption;
//import com.google.cloud.logging.LoggingException;
//import com.google.cloud.logging.Severity;
//import io.grpc.Status;
//import io.grpc.StatusRuntimeException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//import java.util.EnumMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//
///**
// * Default implementation that queries Cloud Logging for per-severity entry
// * counts scoped to a GKE cluster's <em>user-namespace</em> workloads (services)
// * over a rolling one-week window.
// *
// * <p>To reduce both latency and API pressure, each user namespace is queried
// * <strong>once</strong> using a {@code severity>=WARNING} filter, and the five
// * tracked severities are tallied in memory. Namespaces are processed
// * concurrently on a bounded {@link ExecutorService}, and each query is retried
// * with exponential backoff when Cloud Logging returns
// * {@code RESOURCE_EXHAUSTED}.</p>
// */
//@Service
//public class LogSeverityServiceImpl implements LogSeverityService {
//
//    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);
//
//    /** Rolling window over which severities are counted. */
//    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(7);
//
//    /** GKE container log resource type (i.e. service/workload container logs). */
//    private static final String RESOURCE_TYPE = "k8s_container";
//
//    /** Page size used when paging through matching log entries. */
//    private static final int PAGE_SIZE = 1000;
//
//    /** Lowest severity included by the single combined query. */
//    private static final String MIN_SEVERITY = "WARNING";
//
//    /** Empty result reused when no user namespaces are supplied. */
//    private static final LogSeverityCounts EMPTY_COUNTS =
//            new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);
//
//    /**
//     * Single combined filter per namespace (counts all tracked severities at once).
//     * Params: resource type, cluster name, namespace, min severity, RFC3339 start.
//     */
//    private static final String FILTER_TEMPLATE = """
//            resource.type="%s"
//            AND resource.labels.cluster_name="%s"
//            AND resource.labels.namespace_name="%s"
//            AND severity>=%s
//            AND timestamp>="%s"
//            """;
//
//    private final Logging _logging;
//    private final ExecutorService _executor;
//    private final LogSeverityRetryProperties _retryProperties;
//
//    public LogSeverityServiceImpl(
//            Logging logging,
//            @Qualifier(LogSeverityExecutorConfig.LOG_SEVERITY_EXECUTOR_BEAN) ExecutorService executor,
//            LogSeverityRetryProperties retryProperties) {
//        _logging = logging;
//        _executor = executor;
//        _retryProperties = retryProperties;
//    }
//
//    @Override
//    public LogSeverityCounts getWeeklySeverityCounts(
//            String projectId, String clusterId, List<String> userNamespaces) {
//        if (userNamespaces == null || userNamespaces.isEmpty()) {
//            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
//                    clusterId);
//            return EMPTY_COUNTS;
//        }
//
//        _log.info("Counting weekly service log severities for cluster '{}' across {} namespace(s) "
//                + "(one query per namespace, bounded concurrency)", clusterId, userNamespaces.size());
//        try {
//            var startTimestamp = DateTimeFormatter.ISO_INSTANT
//                    .format(Instant.now().minus(LOOKBACK_WINDOW));
//
//            // One future per namespace; each returns that namespace's per-severity tally.
//            var futures = userNamespaces.stream()
//                    .map(namespace -> CompletableFuture.supplyAsync(
//                            () -> countNamespaceSeverities(clusterId, namespace, startTimestamp),
//                            _executor))
//                    .toList();
//
//            var totals = newSeverityMap();
//            futures.forEach(future -> mergeInto(totals, future.join()));
//
//            var counts = toCounts(totals);
//            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
//            return counts;
//        } catch (Exception ex) {
//            _log.error("Failed to count service log severities for cluster '{}' in project '{}'",
//                    clusterId, projectId, ex);
//            throw new ScalingException(
//                    "Failed to count service log severities for cluster: " + clusterId, ex);
//        }
//    }
//
//    /**
//     * Runs a single {@code severity>=WARNING} query for one namespace and tallies
//     * each tracked {@link LogSeverity} in memory. Retries with exponential backoff
//     * on quota exhaustion.
//     */
//    private Map<LogSeverity, Long> countNamespaceSeverities(
//            String clusterId, String namespace, String startTimestamp) {
//        var filter = FILTER_TEMPLATE.formatted(
//                RESOURCE_TYPE, clusterId, namespace, MIN_SEVERITY, startTimestamp);
//
//        var tally = newSeverityMap();
//        var entries = listWithRetry(filter, clusterId, namespace);
//
//        for (var entry : entries) {
//            mapSeverity(entry.getSeverity()).ifPresent(
//                    severity -> tally.merge(severity, 1L, Long::sum));
//        }
//        _log.debug("Cluster '{}' namespace '{}' severity tally = {}", clusterId, namespace, tally);
//        return tally;
//    }
//
//    /**
//     * Executes the Logging list call, retrying on {@code RESOURCE_EXHAUSTED}
//     * using exponential backoff configured via {@link LogSeverityRetryProperties}.
//     */
//    private Iterable<com.google.cloud.logging.LogEntry> listWithRetry(
//            String filter, String clusterId, String namespace) {
//        var attempt = 0;
//        var backoffMillis = _retryProperties.initialBackoffMillis();
//
//        while (true) {
//            try {
//                return _logging.listLogEntries(
//                        EntryListOption.filter(filter),
//                        EntryListOption.pageSize(PAGE_SIZE)).iterateAll();
//            } catch (LoggingException ex) {
//                if (!isQuotaExhausted(ex) || attempt >= _retryProperties.maxRetries()) {
//                    throw ex;
//                }
//                attempt++;
//                _log.warn("Quota exhausted for cluster '{}' namespace '{}'; retry {}/{} after {} ms",
//                        clusterId, namespace, attempt, _retryProperties.maxRetries(), backoffMillis);
//                sleep(backoffMillis);
//                backoffMillis *= _retryProperties.backoffMultiplier();
//            }
//        }
//    }
//
//    /**
//     * @return {@code true} if the exception is a Cloud Logging RESOURCE_EXHAUSTED quota error
//     */
//    private boolean isQuotaExhausted(LoggingException ex) {
//        return ex.getCause() instanceof StatusRuntimeException sre
//                && sre.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
//    }
//
//    private void sleep(long millis) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException ie) {
//            Thread.currentThread().interrupt();
//            throw new ScalingException("Interrupted while backing off on Logging quota", ie);
//        }
//    }
//
//    /** Maps a Cloud Logging {@link Severity} to a tracked {@link LogSeverity}, if applicable. */
//    private java.util.Optional<LogSeverity> mapSeverity(Severity severity) {
//        return switch (severity) {
//            case WARNING -> java.util.Optional.of(LogSeverity.WARNING);
//            case ERROR -> java.util.Optional.of(LogSeverity.ERROR);
//            case CRITICAL -> java.util.Optional.of(LogSeverity.CRITICAL);
//            case ALERT -> java.util.Optional.of(LogSeverity.ALERT);
//            case EMERGENCY -> java.util.Optional.of(LogSeverity.EMERGENCY);
//            default -> java.util.Optional.empty();
//        };
//    }
//
//    private Map<LogSeverity, Long> newSeverityMap() {
//        var map = new EnumMap<LogSeverity, Long>(LogSeverity.class);
//        for (var severity : LogSeverity.values()) {
//            map.put(severity, 0L);
//        }
//        return map;
//    }
//
//    private void mergeInto(Map<LogSeverity, Long> target, Map<LogSeverity, Long> source) {
//        source.forEach((severity, count) -> target.merge(severity, count, Long::sum));
//    }
//
//    private LogSeverityCounts toCounts(Map<LogSeverity, Long> totals) {
//        return new LogSeverityCounts(
//                totals.get(LogSeverity.WARNING),
//                totals.get(LogSeverity.ERROR),
//                totals.get(LogSeverity.CRITICAL),
//                totals.get(LogSeverity.ALERT),
//                totals.get(LogSeverity.EMERGENCY));
//    }
//}

//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.configs.LogSeverityExecutorConfig;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.SeverityCountResult;
//import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
//import com.google.cloud.logging.Logging;
//import com.google.cloud.logging.Logging.EntryListOption;
//import com.google.cloud.logging.LogEntry;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.EnumMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//
///**
// * Default implementation that queries Cloud Logging for per-severity entry
// * counts scoped to a GKE cluster's <em>user-namespace</em> workloads (services)
// * over a rolling one-week window.
// *
// * <p>To reduce latency, each {@code (namespace, severity)} combination is queried
// * concurrently on a shared {@link ExecutorService}: for every user namespace,
// * five tasks (one per {@link LogSeverity}) run in parallel, and all namespaces
// * are processed concurrently. Results are aggregated per severity.</p>
// */
//@Service
//public class LogSeverityServiceImpl implements LogSeverityService {
//
//    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);
//
//    /** Rolling window over which severities are counted. */
//    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(1);
//
//    /** GKE container log resource type (i.e. service/workload container logs). */
//    private static final String RESOURCE_TYPE = "k8s_container";
//
//    /** Page size used when paging through matching log entries. */
//    private static final int PAGE_SIZE = 1000;
//
//    /** Empty result reused when no user namespaces are supplied. */
//    private static final LogSeverityCounts EMPTY_COUNTS = new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);
//
//    /**
//     * Cloud Logging advanced filter template.
//     * Params: resource type, cluster name, namespace, severity token,
//     * RFC3339 start timestamp.
//     */
//    private static final String FILTER_TEMPLATE = """
//            resource.type="%s"
//            AND resource.labels.cluster_name="%s"
//            AND resource.labels.namespace_name="%s"
//            AND severity=%s
//            AND timestamp>="%s"
//            """;
//
//    private final Logging _logging;
//    private final ExecutorService _executor;
//
//    public LogSeverityServiceImpl(
//            Logging logging,
//            @Qualifier(LogSeverityExecutorConfig.LOG_SEVERITY_EXECUTOR_BEAN) ExecutorService executor) {
//        _logging = logging;
//        _executor = executor;
//    }
//
//    @Override
//    public LogSeverityCounts getWeeklySeverityCounts(
//            String projectId, String clusterId, List<String> userNamespaces) {
//        if (userNamespaces == null || userNamespaces.isEmpty()) {
//            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
//                    clusterId);
//            return EMPTY_COUNTS;
//        }
//
//        _log.info("Counting weekly service log severities for cluster '{}' across {} namespace(s) "
//                + "using parallel execution", clusterId, userNamespaces.size());
//        try {
//            var startTimestamp = DateTimeFormatter.ISO_INSTANT
//                    .format(Instant.now().minus(LOOKBACK_WINDOW));
//
//            var futures = submitSeverityTasks(clusterId, userNamespaces, startTimestamp);
//            var counts = aggregate(futures);
//
//            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
//            return counts;
//        } catch (Exception ex) {
//            _log.error("Failed to count service log severities for cluster '{}' in project '{}'",
//                    clusterId, projectId, ex);
//            throw new ScalingException(
//                    "Failed to count service log severities for cluster: " + clusterId, ex);
//        }
//    }
//
//    /**
//     * Submits one asynchronous task per {@code (namespace, severity)} pair.
//     *
//     * @return the in-flight futures, each yielding a single {@link SeverityCountResult}
//     */
//    private List<CompletableFuture<SeverityCountResult>> submitSeverityTasks(
//            String clusterId, List<String> userNamespaces, String startTimestamp) {
//        var futures = new ArrayList<CompletableFuture<SeverityCountResult>>();
//        for (var namespace : userNamespaces) {
//            for (var severity : LogSeverity.values()) {
//                futures.add(CompletableFuture.supplyAsync(
//                        () -> new SeverityCountResult(
//                                severity,
//                                countBySeverity(clusterId, namespace, severity, startTimestamp)),
//                        _executor));
//            }
//        }
//        return futures;
//    }
//
//    /**
//     * Waits for all tasks to complete and sums the counts per severity.
//     */
//    private LogSeverityCounts aggregate(List<CompletableFuture<SeverityCountResult>> futures) {
//        // Block until every task finishes so aggregation reflects all namespaces.
//        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
//
//        var totals = new EnumMap<LogSeverity, Long>(LogSeverity.class);
//        for (var severity : LogSeverity.values()) {
//            totals.put(severity, 0L);
//        }
//        for (var future : futures) {
//            var result = future.join();
//            totals.merge(result.severity(), result.count(), Long::sum);
//        }
//        return toCounts(totals);
//    }
//
//    private LogSeverityCounts toCounts(Map<LogSeverity, Long> totals) {
//        return new LogSeverityCounts(
//                totals.get(LogSeverity.WARNING),
//                totals.get(LogSeverity.ERROR),
//                totals.get(LogSeverity.CRITICAL),
//                totals.get(LogSeverity.ALERT),
//                totals.get(LogSeverity.EMERGENCY));
//    }
//
//    private long countBySeverity(
//            String clusterId, String namespace, LogSeverity severity, String startTimestamp) {
//        var filter = FILTER_TEMPLATE.formatted(
//                RESOURCE_TYPE, clusterId, namespace, severity.value(), startTimestamp);
//
//        var entries = _logging.listLogEntries(
//                EntryListOption.filter(filter),
//                EntryListOption.pageSize(PAGE_SIZE));
//
//        var count = 0L;
//        for (LogEntry ignored : entries.iterateAll()) {
//            count++;
//        }
//        _log.debug("Cluster '{}' namespace '{}' severity {} count = {}",
//                clusterId, namespace, severity.value(), count);
//        return count;
//    }
//}

//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
//import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
//import com.google.cloud.logging.Logging;
//import com.google.cloud.logging.Logging.EntryListOption;
//import com.google.cloud.logging.LogEntry;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * Default implementation that queries Cloud Logging for per-severity entry
// * counts scoped to a GKE cluster's <em>user-namespace</em> workloads (services)
// * over a rolling one-week window. System/control-plane namespace logs are
// * excluded by restricting the filter to the supplied user namespaces.
// */
//@Service
//public class LogSeverityServiceImpl implements LogSeverityService {
//
//    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);
//
//    /** Rolling window over which severities are counted. */
//    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(1);
//
//    /** GKE container log resource type (i.e. service/workload container logs). */
//    private static final String RESOURCE_TYPE = "k8s_container";
//
//    /** Page size used when paging through matching log entries. */
//    private static final int PAGE_SIZE = 1000;
//
//    /** Empty result reused when no user namespaces are supplied. */
//    private static final LogSeverityCounts EMPTY_COUNTS =
//            new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);
//
//    /**
//     * Cloud Logging advanced filter template.
//     * Params: resource type, cluster name, namespace clause, severity token,
//     * RFC3339 start timestamp.
//     */
//    private static final String FILTER_TEMPLATE = """
//            resource.type="%s"
//            AND resource.labels.cluster_name="%s"
//            AND resource.labels.namespace_name=(%s)
//            AND severity=%s
//            AND timestamp>="%s"
//            """;
//
//    /** Separator for the namespace OR-list in the filter (e.g. "ns-a" OR "ns-b"). */
//    private static final String NAMESPACE_OR_SEPARATOR = " OR ";
//
//    private final Logging _logging;
//
//    public LogSeverityServiceImpl(Logging logging) {
//        _logging = logging;
//    }
//
//    @Override
//    public LogSeverityCounts getWeeklySeverityCounts(
//            String projectId, String clusterId, List<String> userNamespaces) {
//        if (userNamespaces == null || userNamespaces.isEmpty()) {
//            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
//                    clusterId);
//            return EMPTY_COUNTS;
//        }
//
//        _log.info("Counting weekly service log severities for cluster '{}' across {} namespace(s)",
//                clusterId, userNamespaces.size());
//        try {
//            var startTimestamp = DateTimeFormatter.ISO_INSTANT
//                    .format(Instant.now().minus(LOOKBACK_WINDOW));
//            var namespaceClause = buildNamespaceClause(userNamespaces);
//
//            var counts = new LogSeverityCounts(
//                    countBySeverity(clusterId, namespaceClause, LogSeverity.WARNING, startTimestamp),
//                    countBySeverity(clusterId, namespaceClause, LogSeverity.ERROR, startTimestamp),
//                    countBySeverity(clusterId, namespaceClause, LogSeverity.CRITICAL, startTimestamp),
//                    countBySeverity(clusterId, namespaceClause, LogSeverity.ALERT, startTimestamp),
//                    countBySeverity(clusterId, namespaceClause, LogSeverity.EMERGENCY, startTimestamp));
//
//            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
//            return counts;
//        } catch (Exception ex) {
//            _log.error("Failed to count service log severities for cluster '{}' in project '{}'",
//                    clusterId, projectId, ex);
//            throw new ScalingException(
//                    "Failed to count service log severities for cluster: " + clusterId, ex);
//        }
//    }
//
//    /**
//     * Builds the quoted OR-list of namespaces for the filter's
//     * {@code namespace_name=(...)} clause.
//     */
//    private String buildNamespaceClause(List<String> userNamespaces) {
//        return userNamespaces.stream()
//                .map(namespace -> "\"" + namespace + "\"")
//                .collect(Collectors.joining(NAMESPACE_OR_SEPARATOR));
//    }
//
//    private long countBySeverity(
//            String clusterId, String namespaceClause, LogSeverity severity, String startTimestamp) {
//        var filter = FILTER_TEMPLATE.formatted(
//                RESOURCE_TYPE, clusterId, namespaceClause, severity.value(), startTimestamp);
//
//        var entries = _logging.listLogEntries(
//                EntryListOption.filter(filter),
//                EntryListOption.pageSize(PAGE_SIZE));
//
//        var count = 0L;
//        for (LogEntry ignored : entries.iterateAll()) {
//            count++;
//        }
//        _log.debug("Cluster '{}' severity {} count = {}", clusterId, severity.value(), count);
//        return count;
//    }
//}
