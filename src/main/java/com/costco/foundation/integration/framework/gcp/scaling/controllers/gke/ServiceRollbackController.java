package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.RollbackRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.RollbackResult;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceRollbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for rolling a service (Deployment) back to a previous revision.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}")
@Tag(name = "Service Rollback",
        description = "Roll a service (Deployment) back to a previous rollout revision")
public class ServiceRollbackController {

    private static final Logger _log = LoggerFactory.getLogger(ServiceRollbackController.class);

    private final ServiceRollbackService _rollbackService;

    public ServiceRollbackController(ServiceRollbackService rollbackService) {
        _rollbackService = rollbackService;
    }

    /**
     * Rolls the service back to the requested revision, or to the immediately
     * previous revision when the body is empty.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the Deployment (service) name
     * @param request     the rollback request (optional target revision)
     * @return the rollback result wrapped in a 200 response
     */
    @PostMapping("/namespaces/{namespace}/services/{serviceName}/rollback")
    @Operation(summary = "Roll back a service",
            description = "Restores a previous revision's pod template, triggering a new rollout. "
                    + "Omit targetRevision to roll back to the immediately previous revision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rollback applied"),
            @ApiResponse(responseCode = "400", description = "Invalid revision or nothing to roll back to"),
            @ApiResponse(responseCode = "404", description = "Cluster or service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<RollbackResult> postRollback(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Namespace name") @PathVariable String namespace,
            @Parameter(description = "Service (Deployment) name") @PathVariable String serviceName,
            @RequestBody(required = false) RollbackRequest request) {
        _log.info("POST rollback: project='{}', cluster='{}', namespace='{}', service='{}'",
                projectId, clusterId, namespace, serviceName);
        var result = _rollbackService.rollback(projectId, clusterId, namespace, serviceName, request);
        return ResponseEntity.ok(result);
    }
}