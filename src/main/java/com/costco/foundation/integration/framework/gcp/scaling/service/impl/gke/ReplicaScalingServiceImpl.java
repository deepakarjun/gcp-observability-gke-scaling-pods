package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.audit.Auditable;
import com.costco.foundation.integration.framework.gcp.scaling.audit.AuditContext;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ScaleRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ScaleToReplicasRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ScaleToReplicasResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.ScaleDirection;
import com.costco.foundation.integration.framework.gcp.scaling.exception.InvalidScaleRequestException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.PodInfoService;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ReplicaScalingService;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ScalingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scales a service to a specific replica count. The desired value is validated
 * against the HPA min/max bounds, and the scaling direction is derived by
 * comparing the desired count with the currently running pods.
 *
 * <p>Reuses {@link PodInfoService} for bounds and current pod counts, and
 * {@link ScalingService} to perform the underlying scale operation.</p>
 */
@Service
public class ReplicaScalingServiceImpl implements ReplicaScalingService {

    private static final Logger _log = LoggerFactory.getLogger(ReplicaScalingServiceImpl.class);

    private final PodInfoService _podInfoService;
    private final ScalingService _scalingService;

    public ReplicaScalingServiceImpl(PodInfoService podInfoService, ScalingService scalingService) {
        this._podInfoService = podInfoService;
        this._scalingService = scalingService;
    }

    @Override
    @Auditable(AuditAction.SCALE)
    public ScaleToReplicasResponse scaleTo(AuditContext auditContext, ScaleToReplicasRequest request) {
        var projectId = request.projectId();
        var namespace = request.namespace();
        var serviceName = request.serviceName();
        var desired = request.desiredReplicas();
        var hpaName = request.hpaName();


        // 1. Read allowed bounds from the HPA.
        var minMax = _podInfoService.getMinMaxPods(projectId, namespace, serviceName, hpaName);
        var min = minMax.minPods();
        var max = minMax.maxPods();

        _log.info("Scale-to request for service '{}': desired={} bounds=[{}, {}]", serviceName, desired, min, max);

        // 2. Validate the desired count is within HPA bounds.
        if (desired < min || desired > max) {
            throw new InvalidScaleRequestException( "Requested replicas %d is outside the allowed HPA range [%d, %d] for service '%s'".formatted(desired, min, max, serviceName));
        }

        // 3. Determine current running pods to derive the direction.
        var current = _podInfoService.getRunningPods(projectId, namespace, serviceName).runningPods();
        var comparison = Integer.compare(desired, current);

        if (comparison == 0) {
            
            _log.info("Service '{}' already running at desired replica count {}", serviceName, desired);

            return ScaleToReplicasResponse.noChange(projectId, namespace, serviceName, current, min, max);
        }

        var direction = comparison > 0 ? ScaleDirection.UP : ScaleDirection.DOWN;

        // 4. Perform the scaling via the existing scaling service.
        _scalingService.scale(new ScaleRequest(namespace, serviceName, direction, desired));

        _log.info("Service '{}' scaled {} from {} to {} replicas", serviceName, direction, current, desired);
        
        return ScaleToReplicasResponse.scaled(projectId, namespace, serviceName, direction, current, desired, min, max);
    }
}