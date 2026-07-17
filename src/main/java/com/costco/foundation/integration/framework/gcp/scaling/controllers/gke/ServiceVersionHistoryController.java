package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceVersionHistory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceVersionHistoryService;
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
 * REST endpoints for retrieving a service's rollout/version history.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}")
@Tag(name = "Service Version History",
        description = "Rollout/version history for services (Deployments) in a GKE cluster")
public class ServiceVersionHistoryController {

    private static final Logger _log =
            LoggerFactory.getLogger(ServiceVersionHistoryController.class);

    private final ServiceVersionHistoryService _versionHistoryService;

    public ServiceVersionHistoryController(ServiceVersionHistoryService versionHistoryService) {
        _versionHistoryService = versionHistoryService;
    }

    /**
     * Returns the version history for a service within a namespace.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the Deployment (service) name
     * @return the version history wrapped in a 200 response
     */
    @GetMapping("/namespaces/{namespace}/services/{serviceName}/versions")
    @Operation(summary = "Get service version history",
            description = "Returns the rollout revisions (images, replicas, timestamps) "
                    + "for a service, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Version history retrieved"),
            @ApiResponse(responseCode = "404", description = "Cluster or service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServiceVersionHistory> getVersionHistory(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Namespace name") @PathVariable String namespace,
            @Parameter(description = "Service (Deployment) name") @PathVariable String serviceName) {
        _log.info("GET version history: project='{}', cluster='{}', namespace='{}', service='{}'",
                projectId, clusterId, namespace, serviceName);
        var history = _versionHistoryService.getVersionHistory(
                projectId, clusterId, namespace, serviceName);
        return ResponseEntity.ok(history);
    }
}