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

/**
 * Default implementation that queries Cloud Logging for per-severity entry
 * counts scoped to a GKE cluster over a rolling one-week window.
 */
@Service
public class LogSeverityServiceImpl implements LogSeverityService {

    private static final Logger _log = LoggerFactory.getLogger(LogSeverityServiceImpl.class);

    /** Rolling window over which severities are counted. */
    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(7);

    /** GKE container log resource type. */
    private static final String RESOURCE_TYPE = "k8s_container";

    /** Page size used when paging through matching log entries. */
    private static final int PAGE_SIZE = 1000;

    /**
     * Cloud Logging advanced filter template.
     * Params: resource type, cluster name, severity token, RFC3339 start timestamp.
     */
    private static final String FILTER_TEMPLATE = """
            resource.type="%s"
            AND resource.labels.cluster_name="%s"
            AND severity=%s
            AND timestamp>="%s"
            """;

    private final Logging _logging;

    public LogSeverityServiceImpl(Logging logging) {
        _logging = logging;
    }

    @Override
    public LogSeverityCounts getWeeklySeverityCounts(String projectId, String clusterId) {
        _log.info("Counting weekly log severities for cluster '{}' in project '{}'", clusterId, projectId);
        try {
            var startTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minus(LOOKBACK_WINDOW));

            var counts = new LogSeverityCounts(
                    countBySeverity(projectId, clusterId, LogSeverity.WARNING, startTimestamp),
                    countBySeverity(projectId, clusterId, LogSeverity.ERROR, startTimestamp),
                    countBySeverity(projectId, clusterId, LogSeverity.CRITICAL, startTimestamp),
                    countBySeverity(projectId, clusterId, LogSeverity.ALERT, startTimestamp),
                    countBySeverity(projectId, clusterId, LogSeverity.EMERGENCY, startTimestamp)
                    );

            _log.info("Weekly severity counts for cluster '{}': {}", clusterId, counts);
            return counts;
        } catch (Exception ex) {
            _log.error("Failed to count log severities for cluster '{}' in project '{}'",
                    clusterId, projectId, ex);
            throw new ScalingException(
                    "Failed to count log severities for cluster: " + clusterId, ex);
        }
    }

    private long countBySeverity(
            String projectId, String clusterId, LogSeverity severity, String startTimestamp) {
        var filter = FILTER_TEMPLATE.formatted(
                RESOURCE_TYPE, clusterId, severity.value(), startTimestamp);

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