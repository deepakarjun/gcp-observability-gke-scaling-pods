package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ResumeRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.SuspendRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceStateResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing service suspend and resume operations.
 */
@RestController
@RequestMapping("/api/v1/lifecycle")
@Tag(name = "Service Lifecycle", description = "Suspend and resume GKE services")
public class ServiceLifecycleController {

    private static final Logger _log = LoggerFactory.getLogger(ServiceLifecycleController.class);

    private final ServiceLifecycleService _lifecycleService;

    public ServiceLifecycleController(ServiceLifecycleService lifecycleService) {
        this._lifecycleService = lifecycleService;
    }

    @PostMapping("/suspend")
    @Operation(summary = "Suspend a service by scaling it down to zero replicas")
    public ResponseEntity<ServiceStateResponse> suspend(@Valid @RequestBody SuspendRequest request) {
        _log.info("Received suspend request for service '{}'", request.serviceName());
        return ResponseEntity.ok(_lifecycleService.suspend(request));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume a suspended service by scaling it back up")
    public ResponseEntity<ServiceStateResponse> resume(@Valid @RequestBody ResumeRequest request) {
        _log.info("Received resume request for service '{}'", request.serviceName());
        return ResponseEntity.ok(_lifecycleService.resume(request));
    }
}