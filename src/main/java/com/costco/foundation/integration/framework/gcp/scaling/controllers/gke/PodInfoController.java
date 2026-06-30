package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.MinMaxPodResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.PodInfoResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.PodInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing pod information for a service.
 */
@RestController
@RequestMapping("/api/v1/pods")
@Tag(name = "Pod Info", description = "Query running, min and max pod counts for a service")
public class PodInfoController {

    private static final Logger _log = LoggerFactory.getLogger(PodInfoController.class);

    private final PodInfoService _podInfoService;

    public PodInfoController(PodInfoService podInfoService) {
        this._podInfoService = podInfoService;
    }

    @GetMapping("/running")
    @Operation(summary = "Get the number of currently running pods for a service")
    public ResponseEntity<PodInfoResponse> getRunningPods(
            @RequestParam String projectId,
            @RequestParam String namespace,
            @RequestParam String serviceName) {
        _log.info("Received running pods request for service '{}'", serviceName);
        return ResponseEntity.ok(_podInfoService.getRunningPods(projectId, namespace, serviceName));
    }

    @GetMapping("/min-max")
    @Operation(summary = "Get the minimum and maximum pod counts for a service")
    public ResponseEntity<MinMaxPodResponse> getMinMaxPods(
            @RequestParam String projectId,
            @RequestParam String namespace,
            @RequestParam String serviceName) {
        _log.info("Received min/max pods request for service '{}'", serviceName);
        return ResponseEntity.ok(_podInfoService.getMinMaxPods(projectId, namespace, serviceName));
    }
}