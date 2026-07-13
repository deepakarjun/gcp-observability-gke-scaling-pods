package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.LogSeverityCounts;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.LogSeverity;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation that queries Cloud Logging for per-severity entry
 * counts scoped to a GKE cluster's <em>user-namespace</em> workloads (services)
 * over a rolling one-week window. System/control-plane namespace logs are
 * excluded by restricting the filter to the supplied user namespaces.
 */
@Service
public class LogSeverityServiceImpl implements LogSeverityService {

    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);

    /** Rolling window over which severities are counted. */
    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(1);

    /** GKE container log resource type (i.e. service/workload container logs). */
    private static final String RESOURCE_TYPE = "k8s_container";

    /** Page size used when paging through matching log entries. */
    private static final int PAGE_SIZE = 1000;

    /** Empty result reused when no user namespaces are supplied. */
    private static final LogSeverityCounts EMPTY_COUNTS =
            new LogSeverityCounts(0L, 0L, 0L, 0L, 0L);

    /**
     * Cloud Logging advanced filter template.
     * Params: resource type, cluster name, namespace clause, severity token,
     * RFC3339 start timestamp.
     */
    private static final String FILTER_TEMPLATE = """
            resource.type="%s"
            AND resource.labels.cluster_name="%s"
            AND resource.labels.namespace_name=(%s)
            AND severity=%s
            AND timestamp>="%s"
            """;

    /** Separator for the namespace OR-list in the filter (e.g. "ns-a" OR "ns-b"). */
    private static final String NAMESPACE_OR_SEPARATOR = " OR ";

    private final Logging _logging;

    public LogSeverityServiceImpl(Logging logging) {
        _logging = logging;
    }

    @Override
    public LogSeverityCounts getWeeklySeverityCounts(
            String projectId, String clusterId, List<String> userNamespaces) {
        if (userNamespaces == null || userNamespaces.isEmpty()) {
            _log.info("No user namespaces supplied for cluster '{}'; returning zero severity counts",
                    clusterId);
            return EMPTY_COUNTS;
        }

        _log.info("Counting weekly service log severities for cluster '{}' across {} namespace(s)",
                clusterId, userNamespaces.size());
        try {
            var startTimestamp = DateTimeFormatter.ISO_INSTANT
                    .format(Instant.now().minus(LOOKBACK_WINDOW));
            var namespaceClause = buildNamespaceClause(userNamespaces);

            var counts = new LogSeverityCounts(
                    countBySeverity(clusterId, namespaceClause, LogSeverity.WARNING, startTimestamp),
                    countBySeverity(clusterId, namespaceClause, LogSeverity.ERROR, startTimestamp),
                    countBySeverity(clusterId, namespaceClause, LogSeverity.CRITICAL, startTimestamp),
                    countBySeverity(clusterId, namespaceClause, LogSeverity.ALERT, startTimestamp),
                    countBySeverity(clusterId, namespaceClause, LogSeverity.EMERGENCY, startTimestamp));

            _log.info("Weekly service severity counts for cluster '{}': {}", clusterId, counts);
            return counts;
        } catch (Exception ex) {
            _log.error("Failed to count service log severities for cluster '{}' in project '{}'",
                    clusterId, projectId, ex);
            throw new ScalingException(
                    "Failed to count service log severities for cluster: " + clusterId, ex);
        }
    }

    /**
     * Builds the quoted OR-list of namespaces for the filter's
     * {@code namespace_name=(...)} clause.
     */
    private String buildNamespaceClause(List<String> userNamespaces) {
        return userNamespaces.stream()
                .map(namespace -> "\"" + namespace + "\"")
                .collect(Collectors.joining(NAMESPACE_OR_SEPARATOR));
    }

    private long countBySeverity(
            String clusterId, String namespaceClause, LogSeverity severity, String startTimestamp) {
        var filter = FILTER_TEMPLATE.formatted(
                RESOURCE_TYPE, clusterId, namespaceClause, severity.value(), startTimestamp);

        var entries = _logging.listLogEntries(
                EntryListOption.filter(filter),
                EntryListOption.pageSize(PAGE_SIZE));

        var count = 0L;
        for (LogEntry ignored : entries.iterateAll()) {
            count++;
        }
        _log.debug("Cluster '{}' severity {} count = {}", clusterId, severity.value(), count);
        return count;
    }
}
