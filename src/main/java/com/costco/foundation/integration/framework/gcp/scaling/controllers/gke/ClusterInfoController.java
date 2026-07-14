package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.NamespaceListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterInfoService;
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
 * Exposes discovery endpoints for GKE clusters and their user namespaces.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters")
@Tag(name = "Cluster Info", description = "Discover GKE clusters and their user-created namespaces")
public class ClusterInfoController {

    private static final Logger _log = LoggerFactory.getLogger(ClusterInfoController.class);

    private final ClusterInfoService _clusterInfoService;

    public ClusterInfoController(ClusterInfoService clusterInfoService) {
        _clusterInfoService = clusterInfoService;
    }

    @GetMapping
    @Operation(
            summary = "List clusters in a project",
            description = "Returns all GKE clusters in the given project across all locations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clusters retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch clusters")
    })
    public ResponseEntity<ClusterListResponse> getClusters(
            @Parameter(description = "GCP project id", required = true)
            @PathVariable String projectId) {
        _log.info("Received request to list clusters for project '{}'", projectId);
        var response = _clusterInfoService.getClusters(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clusterId}/namespaces")
    @Operation(
            summary = "List user namespaces in a cluster",
            description = "Returns user-created namespaces for the given cluster (system namespaces excluded).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Namespaces retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Cluster not found or failed to fetch namespaces")
    })
    public ResponseEntity<NamespaceListResponse> getNamespaces(
            @Parameter(description = "GCP project id", required = true)
            @PathVariable String projectId,
            @Parameter(description = "GKE cluster name", required = true)
            @PathVariable String clusterId) {
        _log.info("Received request to list namespaces for cluster '{}' in project '{}'", clusterId, projectId);
        var response = _clusterInfoService.getUserNamespaces(projectId, clusterId);
        return ResponseEntity.ok(response);
    }
}