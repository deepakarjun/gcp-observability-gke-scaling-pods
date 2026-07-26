package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceHpaResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceHpaService;
import io.kubernetes.client.openapi.apis.AutoscalingV2Api;
import io.kubernetes.client.openapi.models.V2HorizontalPodAutoscaler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Default implementation that resolves the HorizontalPodAutoscaler targeting a
 * service by matching each HPA's {@code scaleTargetRef} against the service name.
 */
@Service
public class ServiceHpaServiceImpl implements ServiceHpaService {

    private static final Logger _log = LoggerFactory.getLogger(ServiceHpaServiceImpl.class);

    /** The {@code scaleTargetRef} kind that identifies a service workload. */
    private static final String TARGET_KIND_DEPLOYMENT = "Deployment";

    private final GkeApiClientFactory _apiClientFactory;

    public ServiceHpaServiceImpl(GkeApiClientFactory apiClientFactory) {
        _apiClientFactory = apiClientFactory;
    }

    @Override
    public ServiceHpaResponse getHpaForService(
            String projectId, String clusterId, String namespace, String serviceName) {
        _log.info("Resolving HPA for service '{}' in namespace '{}', cluster '{}'",
                serviceName, namespace, clusterId);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var autoscalingApi = new AutoscalingV2Api(apiClient);

            var match = autoscalingApi.listNamespacedHorizontalPodAutoscaler(namespace)
                    .execute().getItems().stream()
                    .filter(hpa -> targetsService(hpa, serviceName))
                    .findFirst();

            return match
                    .map(hpa -> toResponse(projectId, clusterId, namespace, serviceName, hpa))
                    .orElseGet(() -> absent(projectId, clusterId, namespace, serviceName));
        } catch (Exception ex) {
            _log.error("Failed to resolve HPA for service '{}' in namespace '{}', cluster '{}'",
                    serviceName, namespace, clusterId, ex);
            throw new ScalingException("Failed to resolve HPA for service: " + serviceName, ex);
        }
    }

    /**
     * @return {@code true} when the HPA's {@code scaleTargetRef} points at the
     *         given service Deployment
     */
    private boolean targetsService(V2HorizontalPodAutoscaler hpa, String serviceName) {
        var targetRef = hpa.getSpec() != null ? hpa.getSpec().getScaleTargetRef() : null;
        if (targetRef == null) {
            return false;
        }
        return TARGET_KIND_DEPLOYMENT.equals(targetRef.getKind())
                && serviceName.equals(targetRef.getName());
    }

    private ServiceHpaResponse toResponse(
            String projectId, String clusterId, String namespace,
            String serviceName, V2HorizontalPodAutoscaler hpa) {
        var spec = hpa.getSpec();
        var status = hpa.getStatus();
        var hpaName = hpa.getMetadata() != null ? hpa.getMetadata().getName() : null;
        var targetKind = spec != null && spec.getScaleTargetRef() != null
                ? spec.getScaleTargetRef().getKind() : null;
        var currentReplicas = status != null ? status.getCurrentReplicas() : null;

        _log.info("Found HPA '{}' targeting service '{}'", hpaName, serviceName);
        return new ServiceHpaResponse(
                projectId, clusterId, namespace, serviceName,
                hpaName, targetKind,
                spec != null ? spec.getMinReplicas() : null,
                spec != null ? spec.getMaxReplicas() : null,
                currentReplicas,
                true);
    }

    private ServiceHpaResponse absent(
            String projectId, String clusterId, String namespace, String serviceName) {
        _log.info("No HPA targets service '{}' in namespace '{}'", serviceName, namespace);
        return new ServiceHpaResponse(
                projectId, clusterId, namespace, serviceName,
                null, null, null, null, null, false);
    }
}