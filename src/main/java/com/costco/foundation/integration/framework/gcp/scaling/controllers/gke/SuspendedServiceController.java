package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.SuspendedServiceListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.SuspendedServiceInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes discovery of suspended (scaled-to-zero) GKE services so they can be
 * selected for resume.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}/namespaces/{namespace}")
@Tag(name = "Suspended Services", description = "Discover suspended (scaled-to-zero) GKE services")
public class SuspendedServiceController {

    private static final Logger _log = LoggerFactory.getLogger(SuspendedServiceController.class);

    private final SuspendedServiceInfoService _suspendedServiceInfoService;

    public SuspendedServiceController(SuspendedServiceInfoService suspendedServiceInfoService) {
        _suspendedServiceInfoService = suspendedServiceInfoService;
    }

    @GetMapping("/suspended-services")
    @Operation(
            summary = "List suspended services",
            description = "Returns deployments scaled to zero replicas in the given namespace, "
                    + "representing services eligible for resume.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suspended services retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Cluster not found or failed to fetch services")
    })
    public ResponseEntity<SuspendedServiceListResponse> getSuspendedServices(
            @Parameter(description = "GCP project id", required = true)
            @PathVariable String projectId,
            @Parameter(description = "GKE cluster name", required = true)
            @PathVariable String clusterId,
            @Parameter(description = "Namespace to inspect", required = true)
            @PathVariable String namespace) {
        _log.info("Received request to list suspended services in namespace '{}' of cluster '{}' (project '{}')",
                namespace, clusterId, projectId);
        var response = _suspendedServiceInfoService.getSuspendedServices(projectId, clusterId, namespace);
        return ResponseEntity.ok(response);
    }
}