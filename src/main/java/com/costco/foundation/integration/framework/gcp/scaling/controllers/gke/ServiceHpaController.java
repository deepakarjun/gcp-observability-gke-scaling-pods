package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceHpaResponse;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceHpaService;
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
 * REST endpoints for resolving the HorizontalPodAutoscaler of a service.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/clusters/{clusterId}"
        + "/namespaces/{namespace}/services/{serviceName}")
@Tag(name = "Service HPA",
        description = "Resolve the HorizontalPodAutoscaler associated with a running service")
public class ServiceHpaController {

    private static final Logger _log = LoggerFactory.getLogger(ServiceHpaController.class);

    private final ServiceHpaService _hpaService;

    public ServiceHpaController(ServiceHpaService hpaService) {
        _hpaService = hpaService;
    }

    /**
     * Returns the HPA that targets the given service, or a response with
     * {@code present=false} when none is configured.
     *
     * @param projectId   the GCP project id
     * @param clusterId   the cluster name
     * @param namespace   the namespace containing the service
     * @param serviceName the service (Deployment) name
     * @return the HPA details wrapped in a 200 response
     */
    @GetMapping("/hpa")
    @Operation(summary = "Get service HPA",
            description = "Returns the HorizontalPodAutoscaler name and limits targeting the "
                    + "service. Responds with present=false when no HPA is configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HPA resolved (may be absent)"),
            @ApiResponse(responseCode = "404", description = "Cluster or namespace not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServiceHpaResponse> getServiceHpa(
            @Parameter(description = "GCP project id") @PathVariable String projectId,
            @Parameter(description = "Cluster name") @PathVariable String clusterId,
            @Parameter(description = "Namespace name") @PathVariable String namespace,
            @Parameter(description = "Service (Deployment) name") @PathVariable String serviceName) {
        _log.info("GET service HPA: project='{}', cluster='{}', namespace='{}', service='{}'",
                projectId, clusterId, namespace, serviceName);
        var response = _hpaService.getHpaForService(projectId, clusterId, namespace, serviceName);
        return ResponseEntity.ok(response);
    }
}