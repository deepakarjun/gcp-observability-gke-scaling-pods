package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.UtilizationStreamProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterUtilizationResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationTimeRange;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterUtilizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST + SSE endpoints delivering CPU, Memory, and Disk utilization for GKE
 * clusters, powering real-time streaming line-chart widgets.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}/utilization")
@Tag(name = "Cluster Utilization",
        description = "Streaming CPU/Memory/Disk utilization metrics for a GKE cluster")
public class ClusterUtilizationController {

    private static final Logger _log = LoggerFactory.getLogger(ClusterUtilizationController.class);

    /** SSE event name for utilization updates. */
    private static final String UTILIZATION_EVENT = "utilization";

    /** Dedicated scheduler for pushing periodic SSE samples. */
    private final ScheduledExecutorService _scheduler =
            Executors.newScheduledThreadPool(4);

    private final ClusterUtilizationService _utilizationService;
    private final UtilizationStreamProperties _streamProperties;

    public ClusterUtilizationController(
            ClusterUtilizationService utilizationService,
            UtilizationStreamProperties streamProperties) {
        _utilizationService = utilizationService;
        _streamProperties = streamProperties;
    }

    /**
     * Returns the initial/historical utilization window. Uses a 30-minute default
     * when neither a preset {@code range} nor a custom start/end is supplied.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @param range     optional preset (ONE_MIN, FIVE_MINS, TEN_MINS, THIRTY_MINS, CUSTOM)
     * @param startTime custom window start (required when range=CUSTOM)
     * @param endTime   custom window end (required when range=CUSTOM)
     * @return the utilization response wrapped in a 200
     */
    @GetMapping
    @Operation(summary = "Get cluster utilization",
            description = "Returns CPU/Memory/Disk series for the selected window "
                    + "(default: last 30 minutes).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilization retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid range or custom window"),
            @ApiResponse(responseCode = "404", description = "Cluster not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ClusterUtilizationResponse> getUtilization(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Preset time range; omit for 30-min default")
            @RequestParam(required = false) UtilizationTimeRange range,
            @Parameter(description = "Custom start (ISO-8601, UTC); required when range=CUSTOM")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "Custom end (ISO-8601, UTC); required when range=CUSTOM")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        _log.info("GET utilization: project='{}', cluster='{}', range='{}'",
                projectId, clusterId, range);

        var response = (range == UtilizationTimeRange.CUSTOM || startTime != null || endTime != null)
                ? _utilizationService.getUtilization(projectId, clusterId, startTime, endTime)
                : _utilizationService.getUtilization(projectId, clusterId, range);
        return ResponseEntity.ok(response);
    }

    /**
     * Opens a Server-Sent Events stream that pushes the latest utilization sample
     * at the configured cadence, feeding a real-time line chart.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @return an {@link SseEmitter} streaming {@code utilization} events
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream cluster utilization (SSE)",
            description = "Server-Sent Events stream emitting the latest CPU/Memory/Disk "
                    + "sample on a fixed cadence for live charts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened"),
            @ApiResponse(responseCode = "404", description = "Cluster not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public SseEmitter streamUtilization(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId) {
        _log.info("Opening utilization SSE stream: project='{}', cluster='{}'", projectId, clusterId);

        var emitter = new SseEmitter(_streamProperties.streamTimeoutMillis());
        var task = scheduleStreaming(emitter, projectId, clusterId);
        registerCleanup(emitter, task, clusterId);
        return emitter;
    }

    /**
     * Schedules periodic pushes of the latest utilization sample to the emitter.
     */
    private ScheduledFuture<?> scheduleStreaming(
            SseEmitter emitter, String projectId, String clusterId) {
        var interval = _streamProperties.streamIntervalMillis();
        return _scheduler.scheduleAtFixedRate(() -> {
            try {
                var latest = _utilizationService.getLatest(projectId, clusterId);
                emitter.send(SseEmitter.event().name(UTILIZATION_EVENT).data(latest));
            } catch (IOException ioEx) {
                _log.warn("SSE client disconnected for cluster '{}': {}",
                        clusterId, ioEx.getMessage());
                emitter.complete();
            } catch (Exception ex) {
                _log.error("Error streaming utilization for cluster '{}'", clusterId, ex);
                emitter.completeWithError(ex);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels the scheduled task when the SSE connection ends (completion,
     * timeout, or error) to avoid leaking threads.
     */
    private void registerCleanup(SseEmitter emitter, ScheduledFuture<?> task, String clusterId) {
        emitter.onCompletion(() -> {
            task.cancel(true);
            _log.info("Closed utilization SSE stream for cluster '{}'", clusterId);
        });
        emitter.onTimeout(() -> {
            task.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> task.cancel(true));
    }
}