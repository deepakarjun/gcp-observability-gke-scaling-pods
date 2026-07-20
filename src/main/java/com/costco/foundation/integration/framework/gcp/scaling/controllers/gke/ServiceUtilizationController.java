package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.UtilizationStreamProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceUtilizationResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.UtilizationTimeRange;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceUtilizationService;
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
 * REST + SSE endpoints delivering CPU, Memory, and Disk utilization for a
 * running service (container) within a namespace, powering real-time streaming
 * line-chart widgets.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}"
        + "/namespaces/{namespace}/services/{serviceName}/utilization")
@Tag(name = "Service Utilization",
        description = "Streaming CPU/Memory/Disk utilization metrics for a running GKE service")
public class ServiceUtilizationController {

    private static final Logger _log = LoggerFactory.getLogger(ServiceUtilizationController.class);

    /** SSE event name for utilization updates. */
    private static final String UTILIZATION_EVENT = "utilization";

    /** Sub-path for the Server-Sent Events streaming endpoint. */
    private static final String STREAM_PATH = "/stream";

    /** Dedicated scheduler for pushing periodic SSE samples. */
    private final ScheduledExecutorService _scheduler = Executors.newScheduledThreadPool(4);

    private final ServiceUtilizationService _utilizationService;
    private final UtilizationStreamProperties _streamProperties;

    public ServiceUtilizationController(
            ServiceUtilizationService utilizationService,
            UtilizationStreamProperties streamProperties) {
        _utilizationService = utilizationService;
        _streamProperties = streamProperties;
    }

    /**
     * Returns the initial/historical utilization window for the service. Uses a
     * 30-minute default when neither a preset {@code range} nor a custom
     * start/end is supplied.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the service (container) name
     * @param range       optional preset (ONE_MIN, FIVE_MINS, TEN_MINS, THIRTY_MINS, CUSTOM)
     * @param startTime   custom window start (required when range=CUSTOM)
     * @param endTime     custom window end (required when range=CUSTOM)
     * @return the utilization response wrapped in a 200
     */
    @GetMapping
    @Operation(summary = "Get service utilization",
            description = "Returns CPU/Memory/Disk series for the selected window "
                    + "(default: last 30 minutes).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilization retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid range or custom window"),
            @ApiResponse(responseCode = "404", description = "Cluster or service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServiceUtilizationResponse> getUtilization(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Namespace name") @PathVariable String namespace,
            @Parameter(description = "Service (container) name") @PathVariable String serviceName,
            @Parameter(description = "Preset time range; omit for 30-min default")
            @RequestParam(required = false) UtilizationTimeRange range,
            @Parameter(description = "Custom start (ISO-8601, UTC); required when range=CUSTOM")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "Custom end (ISO-8601, UTC); required when range=CUSTOM")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        _log.info("GET service utilization: project='{}', cluster='{}', namespace='{}', service='{}', range='{}'",
                projectId, clusterId, namespace, serviceName, range);

        var response = (range == UtilizationTimeRange.CUSTOM || startTime != null || endTime != null)
                ? _utilizationService.getUtilization(
                        projectId, clusterId, namespace, serviceName, startTime, endTime)
                : _utilizationService.getUtilization(
                        projectId, clusterId, namespace, serviceName, range);
        return ResponseEntity.ok(response);
    }

    /**
     * Opens a Server-Sent Events stream that pushes the latest utilization sample
     * for the service at the configured cadence, feeding a real-time line chart.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the service (container) name
     * @return an {@link SseEmitter} streaming {@code utilization} events
     */
    @GetMapping(value = STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream service utilization (SSE)",
            description = "Server-Sent Events stream emitting the latest CPU/Memory/Disk "
                    + "sample for the service on a fixed cadence for live charts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened"),
            @ApiResponse(responseCode = "404", description = "Cluster or service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public SseEmitter streamUtilization(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Namespace name") @PathVariable String namespace,
            @Parameter(description = "Service (container) name") @PathVariable String serviceName) {
        _log.info("Opening service utilization SSE stream: project='{}', cluster='{}', namespace='{}', service='{}'",
                projectId, clusterId, namespace, serviceName);

        var emitter = new SseEmitter(_streamProperties.streamTimeoutMillis());
        var task = scheduleStreaming(emitter, projectId, clusterId, namespace, serviceName);
        registerCleanup(emitter, task, serviceName);
        return emitter;
    }

    /**
     * Schedules periodic pushes of the latest utilization sample to the emitter.
     */
    private ScheduledFuture<?> scheduleStreaming(
            SseEmitter emitter, String projectId, String clusterId,
            String namespace, String serviceName) {
        var interval = _streamProperties.streamIntervalMillis();
        return _scheduler.scheduleAtFixedRate(() -> {
            try {
                var latest = _utilizationService.getLatest(
                        projectId, clusterId, namespace, serviceName);
                emitter.send(SseEmitter.event().name(UTILIZATION_EVENT).data(latest));
            } catch (IOException ioEx) {
                _log.warn("SSE client disconnected for service '{}': {}",
                        serviceName, ioEx.getMessage());
                emitter.complete();
            } catch (Exception ex) {
                _log.error("Error streaming utilization for service '{}'", serviceName, ex);
                emitter.completeWithError(ex);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels the scheduled task when the SSE connection ends (completion,
     * timeout, or error) to avoid leaking threads.
     */
    private void registerCleanup(SseEmitter emitter, ScheduledFuture<?> task, String serviceName) {
        emitter.onCompletion(() -> {
            task.cancel(true);
            _log.info("Closed service utilization SSE stream for service '{}'", serviceName);
        });
        emitter.onTimeout(() -> {
            task.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> task.cancel(true));
    }
}