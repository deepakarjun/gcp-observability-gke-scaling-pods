package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterKeyPoints;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterKeyPointsService;
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
 * Exposes an aggregated "key points" summary for a selected GKE cluster.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}")
@Tag(name = "Cluster Key Points", description = "Aggregated summary metrics for a GKE cluster")
public class ClusterKeyPointsController {

    private static final Logger _log = LoggerFactory.getLogger(ClusterKeyPointsController.class);

    private final ClusterKeyPointsService _clusterKeyPointsService;

    public ClusterKeyPointsController(ClusterKeyPointsService clusterKeyPointsService) {
        _clusterKeyPointsService = clusterKeyPointsService;
    }

    @GetMapping("/key-points")
    @Operation(
            summary = "Get cluster key points",
            description = "Returns aggregated counts for the cluster: user namespaces, running services, "
                    + "suspended services, active pods, and total containers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key points computed successfully"),
            @ApiResponse(responseCode = "500", description = "Cluster not found or failed to compute key points")
    })
    public ResponseEntity<ClusterKeyPoints> getKeyPoints(
            @Parameter(description = "GCP project id", required = true)
            @PathVariable String projectId,
            @Parameter(description = "GKE cluster name", required = true)
            @PathVariable String clusterId) {
        _log.info("Received request for key points of cluster '{}' in project '{}'", clusterId, projectId);
        var keyPoints = _clusterKeyPointsService.getKeyPoints(projectId, clusterId);
        return ResponseEntity.ok(keyPoints);
    }
}
