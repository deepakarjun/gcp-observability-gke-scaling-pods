package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.audit.Auditable;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ResumeRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ScaleRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.SuspendRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceStateResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ScaleDirection;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ServiceState;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ScalingService;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceLifecycleService;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Suspends a service by scaling it to zero replicas and resumes it by
 * scaling back up. Reuses {@link ScalingService} for the underlying scaling.
 */
@Service
public class ServiceLifecycleServiceImpl implements ServiceLifecycleService {

    private static final Logger _log = LoggerFactory.getLogger(ServiceLifecycleServiceImpl.class);
    private static final int SUSPENDED_REPLICAS = 0;
    private static final int DEFAULT_REPLICAS = 1;
    private static final String HPA_SUFFIX = "-hpa";

    private final ScalingService _scalingService;
    private final AutoscalingV1Api _autoscalingV1Api;

    public ServiceLifecycleServiceImpl(ScalingService scalingService,
                                       AutoscalingV1Api autoscalingV1Api) {
        this._scalingService = scalingService;
        this._autoscalingV1Api = autoscalingV1Api;
    }

    @Override
    @Auditable(AuditAction.SUSPEND)
    public ServiceStateResponse suspend(SuspendRequest request) {
        _log.info("Suspending service '{}' in namespace '{}' (project '{}')",
                request.serviceName(), request.namespace(), request.projectId());

        var scaleRequest = new ScaleRequest(request.namespace(), request.serviceName(),
                ScaleDirection.DOWN, SUSPENDED_REPLICAS);
        _scalingService.scale(scaleRequest);

        return ServiceStateResponse.of(request.projectId(), request.namespace(),
                request.serviceName(), ServiceState.SUSPENDED, SUSPENDED_REPLICAS,
                "Service suspended successfully");
    }

    @Override
    @Auditable(AuditAction.RESUME)
    public ServiceStateResponse resume(ResumeRequest request) {
        var replicas = resolveResumeReplicas(request);
        _log.info("Resuming service '{}' in namespace '{}' to {} replicas (project '{}')",
                request.serviceName() + HPA_SUFFIX, request.namespace(), replicas, request.projectId());

        var scaleRequest = new ScaleRequest(request.namespace(), request.serviceName(),
                ScaleDirection.UP, replicas);
        _scalingService.scale(scaleRequest);

        return ServiceStateResponse.of(request.projectId(), request.namespace(),
                request.serviceName() + HPA_SUFFIX, ServiceState.RUNNING, replicas,
                "Service resumed successfully");
    }

    /**
     * Determines the replica count to use on resume: the explicit request value
     * if provided, otherwise the HPA minimum, falling back to a safe default.
     */
    private int resolveResumeReplicas(ResumeRequest request) {
        if (request.replicas() != null && request.replicas() > 0) {
            return request.replicas();
        }

        var hpaName = request.serviceName() + HPA_SUFFIX;
        try {
            var hpa = _autoscalingV1Api
                    .readNamespacedHorizontalPodAutoscaler(hpaName, request.namespace())
                    .execute();

            var spec = hpa.getSpec();
            if (spec != null && spec.getMinReplicas() != null && spec.getMinReplicas() > 0) {
                return spec.getMinReplicas();
            }
        } catch (ApiException e) {
            _log.warn("Could not read HPA min replicas for '{}', defaulting to {}: {}",
                    hpaName, DEFAULT_REPLICAS, e.getResponseBody());
        }
        return DEFAULT_REPLICAS;
    }
}
