package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.RollbackRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.RollbackResult;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceRollbackService;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation that rolls a Deployment back by restoring the pod
 * template of a previous ReplicaSet revision. This mirrors
 * {@code kubectl rollout undo}: locate the target revision's ReplicaSet, copy
 * its pod template into the Deployment, and patch it to trigger a new rollout.
 */
@Service
public class ServiceRollbackServiceImpl implements ServiceRollbackService {

    private static final Logger _log = LoggerFactory.getLogger(ServiceRollbackServiceImpl.class);

    /** Annotation carrying the rollout revision number on ReplicaSets/Deployments. */
    private static final String REVISION_ANNOTATION = "deployment.kubernetes.io/revision";

    /** Records the reason for a rollout, surfaced in rollout history. */
    private static final String CHANGE_CAUSE_ANNOTATION = "kubernetes.io/change-cause";

    /** Sentinel meaning "roll back to the immediately previous revision". */
    private static final long PREVIOUS_REVISION_SENTINEL = 0L;

    /** Revision value used when the annotation is absent or unparsable. */
    private static final long UNKNOWN_REVISION = 0L;

    private final GkeApiClientFactory _apiClientFactory;

    public ServiceRollbackServiceImpl(GkeApiClientFactory apiClientFactory) {
        _apiClientFactory = apiClientFactory;
    }

    @Override
    public RollbackResult rollback(
            String projectId, String clusterId, String namespace,
            String serviceName, RollbackRequest request) {
        var requestedRevision = resolveRequestedRevision(request);
        _log.info("Rolling back service '{}' in namespace '{}', cluster '{}' (target revision: {})",
                serviceName, namespace, clusterId,
                requestedRevision == PREVIOUS_REVISION_SENTINEL ? "previous" : requestedRevision);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var appsApi = new AppsV1Api(apiClient);

            var deployment = appsApi.readNamespacedDeployment(serviceName, namespace).execute();
            var currentRevision = revisionOf(deployment.getMetadata());

            var replicaSets = listOwnedReplicaSets(appsApi, namespace, serviceName);
            var target = selectTargetReplicaSet(replicaSets, currentRevision, requestedRevision);
            var targetRevision = revisionOf(target.getMetadata());

            applyRollback(appsApi, namespace, serviceName, deployment, target, targetRevision);

            var result = new RollbackResult(
                    projectId, clusterId, namespace, serviceName,
                    currentRevision, targetRevision,
                    extractImages(target), OffsetDateTime.now(ZoneOffset.UTC));
            _log.info("Rolled back service '{}' from revision {} to {}",
                    serviceName, currentRevision, targetRevision);
            return result;
        } catch (ScalingException ex) {
            throw ex; // preserve context (e.g. cluster/service not found)
        } catch (Exception ex) {
            _log.error("Failed to roll back service '{}' in namespace '{}', cluster '{}'",
                    serviceName, namespace, clusterId, ex);
            throw new ScalingException("Failed to roll back service: " + serviceName, ex);
        }
    }

    private long resolveRequestedRevision(RollbackRequest request) {
        if (request == null || request.targetRevision() == null
                || request.targetRevision() <= 0) {
            return PREVIOUS_REVISION_SENTINEL;
        }
        return request.targetRevision();
    }

    /**
     * Chooses the ReplicaSet to restore: the explicitly requested revision, or
     * the highest revision strictly below the current one (i.e. "previous").
     */
    private V1ReplicaSet selectTargetReplicaSet(
            List<V1ReplicaSet> replicaSets, long currentRevision, long requestedRevision) {
        if (replicaSets.isEmpty()) {
            throw new ScalingException("No ReplicaSet revisions found to roll back to");
        }
        return switch (Long.signum(requestedRevision)) {
            case 0 -> selectPreviousRevision(replicaSets, currentRevision);
            default -> selectSpecificRevision(replicaSets, requestedRevision, currentRevision);
        };
    }

    private V1ReplicaSet selectPreviousRevision(
            List<V1ReplicaSet> replicaSets, long currentRevision) {
        return replicaSets.stream()
                .filter(rs -> revisionOf(rs.getMetadata()) < currentRevision
                        && revisionOf(rs.getMetadata()) != UNKNOWN_REVISION)
                .max(Comparator.comparingLong(rs -> revisionOf(rs.getMetadata())))
                .orElseThrow(() -> new ScalingException(
                        "No previous revision available to roll back to (current revision: "
                                + currentRevision + ")"));
    }

    private V1ReplicaSet selectSpecificRevision(
            List<V1ReplicaSet> replicaSets, long requestedRevision, long currentRevision) {
        if (requestedRevision == currentRevision) {
            throw new ScalingException(
                    "Requested revision " + requestedRevision + " is already the current revision");
        }
        return replicaSets.stream()
                .filter(rs -> revisionOf(rs.getMetadata()) == requestedRevision)
                .findFirst()
                .orElseThrow(() -> new ScalingException(
                        "Requested revision not found: " + requestedRevision));
    }

    /**
     * Copies the target ReplicaSet's pod template into the Deployment and patches
     * it, recording a change cause. Kubernetes then rolls out the restored spec.
     */
    private void applyRollback(
            AppsV1Api appsApi, String namespace, String serviceName,
            V1Deployment deployment, V1ReplicaSet target, long targetRevision) throws Exception {
        var targetTemplate = Optional.ofNullable(target.getSpec())
                .map(spec -> spec.getTemplate())
                .orElseThrow(() -> new ScalingException(
                        "Target revision has no pod template: " + targetRevision));

        var spec = Optional.ofNullable(deployment.getSpec())
                .orElseThrow(() -> new ScalingException(
                        "Deployment has no spec: " + serviceName));
        spec.setTemplate(targetTemplate);

        var metadata = deployment.getMetadata();
        if (metadata != null) {
            metadata.putAnnotationsItem(CHANGE_CAUSE_ANNOTATION,
                    "Rolled back to revision " + targetRevision);
        }

        appsApi.replaceNamespacedDeployment(serviceName, namespace, deployment).execute();
    }

    private List<V1ReplicaSet> listOwnedReplicaSets(
            AppsV1Api appsApi, String namespace, String serviceName) throws Exception {
        return appsApi.listNamespacedReplicaSet(namespace).execute().getItems().stream()
                .filter(rs -> isOwnedBy(rs, serviceName))
                .toList();
    }

    private boolean isOwnedBy(V1ReplicaSet replicaSet, String serviceName) {
        var owners = replicaSet.getMetadata() != null
                ? replicaSet.getMetadata().getOwnerReferences() : null;
        if (owners == null) {
            return false;
        }
        return owners.stream().anyMatch(owner ->
                "Deployment".equals(owner.getKind()) && serviceName.equals(owner.getName()));
    }

    private List<String> extractImages(V1ReplicaSet replicaSet) {
        var podSpec = Optional.ofNullable(replicaSet.getSpec())
                .map(spec -> spec.getTemplate())
                .map(template -> template.getSpec())
                .orElse(null);
        if (podSpec == null || podSpec.getContainers() == null) {
            return List.of();
        }
        return podSpec.getContainers().stream()
                .map(container -> container.getImage())
                .filter(image -> image != null)
                .toList();
    }

    /**
     * Reads the revision annotation from the given metadata, returning
     * {@link #UNKNOWN_REVISION} when missing or malformed.
     */
    private long revisionOf(io.kubernetes.client.openapi.models.V1ObjectMeta metadata) {
        var annotations = metadata != null ? metadata.getAnnotations() : null;
        var raw = annotations != null ? annotations.get(REVISION_ANNOTATION) : null;
        if (raw == null || raw.isBlank()) {
            return UNKNOWN_REVISION;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            _log.warn("Unparsable revision annotation '{}'; defaulting to {}",
                    raw, UNKNOWN_REVISION);
            return UNKNOWN_REVISION;
        }
    }
}
