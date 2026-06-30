package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

//public class ScalingServiceImpl {
//
//}
//
//package com.costco.scaling.service.impl;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ScaleRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ScaleResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ScalingService;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.openapi.models.V1ScaleSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation that scales GKE deployments via the Kubernetes API.
 */
@Service
public class ScalingServiceImpl implements ScalingService {

    private static final Logger _log = LoggerFactory.getLogger(ScalingServiceImpl.class);

    private final AppsV1Api _appsV1Api;

    public ScalingServiceImpl(AppsV1Api appsV1Api) {
        this._appsV1Api = appsV1Api;
    }

    @Override
    public ScaleResponse scale(ScaleRequest request) {
        var replicas = switch (request.direction()) {
            case UP, DOWN -> request.replicas();
        };

        try {
            _log.info("Scaling {} deployment '{}' in namespace '{}' to {} replicas",
                    request.direction(), request.deploymentName(), request.namespace(), replicas);

//            var scale = new V1Scale().spec(new V1ScaleSpec().replicas(replicas));
            var scale = new V1Scale().metadata(new V1ObjectMeta().name(request.deploymentName())).spec(new V1ScaleSpec().replicas(replicas));
            
            _appsV1Api.replaceNamespacedDeploymentScale(
                    request.deploymentName(), request.namespace(), scale,
                    null, null, null, null);

            return ScaleResponse.success(request.namespace(), request.deploymentName(),
                    request.direction(), replicas);
        } catch (ApiException e) {
            _log.error("Failed to scale deployment '{}': {}", request.deploymentName(),
                    e.getResponseBody(), e);
            throw new ScalingException("Failed to scale deployment: " + request.deploymentName(), e);
        }
    }
}