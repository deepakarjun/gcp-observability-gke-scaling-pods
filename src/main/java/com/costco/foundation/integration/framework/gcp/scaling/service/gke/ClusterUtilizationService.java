package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterUtilizationResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationTimeRange;

import java.time.OffsetDateTime;

/**
 * Provides CPU, Memory, and Disk utilization metrics for a GKE cluster via the
 * Cloud Monitoring API.
 */
public interface ClusterUtilizationService {

    /**
     * Returns utilization series over a preset window (or default when null).
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @param range     the selected preset; {@code null} uses {@link UtilizationTimeRange#DEFAULT}
     * @return the utilization response
     */
    ClusterUtilizationResponse getUtilization(
            String projectId, String clusterId, UtilizationTimeRange range);

    /**
     * Returns utilization series over an explicit custom range.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @param startTime custom window start (UTC)
     * @param endTime   custom window end (UTC)
     * @return the utilization response
     */
    ClusterUtilizationResponse getUtilization(
            String projectId, String clusterId,
            OffsetDateTime startTime, OffsetDateTime endTime);

    /**
     * Returns the latest single sample per metric, used to push live updates on
     * the streaming endpoint.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @return the utilization response containing the most recent point(s)
     */
    ClusterUtilizationResponse getLatest(String projectId, String clusterId);
}