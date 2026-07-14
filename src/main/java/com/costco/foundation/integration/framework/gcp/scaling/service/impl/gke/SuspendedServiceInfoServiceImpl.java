package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.SuspendedServiceInfo;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.SuspendedServiceListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.SuspendedServiceInfoService;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation that lists deployments scaled to zero replicas,
 * treating them as suspended/stopped services eligible for resume.
 */
@Service
public class SuspendedServiceInfoServiceImpl implements SuspendedServiceInfoService {

    private static final Logger _log = LoggerFactory.getLogger(SuspendedServiceInfoServiceImpl.class);

    /** A deployment is considered suspended when its desired replica count is this value. */
    private static final int SUSPENDED_REPLICA_COUNT = 0;

    private final GkeApiClientFactory _apiClientFactory;

    public SuspendedServiceInfoServiceImpl(GkeApiClientFactory apiClientFactory) {
        _apiClientFactory = apiClientFactory;
    }

    @Override
    public SuspendedServiceListResponse getSuspendedServices(
            String projectId, String clusterId, String namespace) {
        _log.info("Fetching suspended services in namespace '{}' of cluster '{}' (project '{}')",
                namespace, clusterId, projectId);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var appsApi = new AppsV1Api(apiClient);

            var deployments = appsApi.listNamespacedDeployment(namespace).execute();
            var suspended = deployments.getItems().stream()
                    .filter(this::isSuspended)
                    .map(deployment -> toSuspendedServiceInfo(deployment, namespace))
                    .toList();

            _log.info("Found {} suspended service(s) in namespace '{}' of cluster '{}'",
                    suspended.size(), namespace, clusterId);
            return new SuspendedServiceListResponse(
                    projectId, clusterId, namespace, suspended.size(), suspended);
        } catch (ScalingException ex) {
            throw ex; // preserve "cluster not found" context from the factory
        } catch (Exception ex) {
            _log.error("Failed to fetch suspended services in namespace '{}' of cluster '{}'",
                    namespace, clusterId, ex);
            throw new ScalingException(
                    "Failed to fetch suspended services for namespace: " + namespace, ex);
        }
    }

    private boolean isSuspended(V1Deployment deployment) {
        var replicas = deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null;
        return replicas != null && replicas == SUSPENDED_REPLICA_COUNT;
    }

    private SuspendedServiceInfo toSuspendedServiceInfo(V1Deployment deployment, String namespace) {
        var name = deployment.getMetadata() != null ? deployment.getMetadata().getName() : null;
        var desired = deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null;
        var available = deployment.getStatus() != null
                ? deployment.getStatus().getAvailableReplicas() : null;

        return new SuspendedServiceInfo(
                name,
                namespace,
                desired != null ? desired : 0,
                available != null ? available : 0);
    }
}
