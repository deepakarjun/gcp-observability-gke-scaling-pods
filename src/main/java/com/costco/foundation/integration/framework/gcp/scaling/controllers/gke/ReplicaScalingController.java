package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ScaleToReplicasRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ScaleToReplicasResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ReplicaScalingService;
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
 * REST controller exposing scale-to-replicas operations.
 */
@RestController
@RequestMapping("/api/v1/scaling")
@Tag(name = "Replica Scaling",
        description = "Scale a service up or down to a specific replica count within HPA bounds")
public class ReplicaScalingController {

    private static final Logger _log = LoggerFactory.getLogger(ReplicaScalingController.class);

    private final ReplicaScalingService _replicaScalingService;

    public ReplicaScalingController(ReplicaScalingService replicaScalingService) {
        this._replicaScalingService = replicaScalingService;
    }

    @PostMapping("/scale-to")
    @Operation(summary = "Scale a service to a specific replica count, validated against HPA min/max")
    public ResponseEntity<ScaleToReplicasResponse> scaleTo(
            @Valid @RequestBody ScaleToReplicasRequest request) {
        _log.info("Received scale-to request for service '{}' -> {} replicas",
                request.serviceName(), request.desiredReplicas());
        return ResponseEntity.ok(_replicaScalingService.scaleTo(request));
    }
}