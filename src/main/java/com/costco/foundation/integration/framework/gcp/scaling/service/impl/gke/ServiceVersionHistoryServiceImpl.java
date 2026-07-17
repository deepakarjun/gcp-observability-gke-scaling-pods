package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceVersion;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceVersionHistory;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ServiceVersionHistoryService;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation that reconstructs a service's version history from the
 * ReplicaSets owned by its Deployment. Each ReplicaSet represents a rollout
 * revision (via the {@code deployment.kubernetes.io/revision} annotation) and
 * captures the container images deployed at that revision.
 */
@Service
public class ServiceVersionHistoryServiceImpl implements ServiceVersionHistoryService {

    private static final Logger _log =
            LoggerFactory.getLogger(ServiceVersionHistoryServiceImpl.class);

    /** Annotation carrying the rollout revision number on ReplicaSets/Deployments. */
    private static final String REVISION_ANNOTATION = "deployment.kubernetes.io/revision";

    /** Default revision used when the annotation is absent or unparsable. */
    private static final long UNKNOWN_REVISION = 0L;

    private final GkeApiClientFactory _apiClientFactory;

    public ServiceVersionHistoryServiceImpl(GkeApiClientFactory apiClientFactory) {
        _apiClientFactory = apiClientFactory;
    }

    @Override
    public ServiceVersionHistory getVersionHistory(
            String projectId, String clusterId, String namespace, String serviceName) {
        _log.info("Fetching version history for service '{}' in namespace '{}', cluster '{}'",
                serviceName, namespace, clusterId);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var appsApi = new AppsV1Api(apiClient);

            var currentRevision = resolveCurrentRevision(appsApi, namespace, serviceName);
            var replicaSets = listOwnedReplicaSets(appsApi, namespace, serviceName);

            var versions = replicaSets.stream()
                    .map(rs -> toServiceVersion(rs, currentRevision))
                    .sorted(Comparator.comparingLong(ServiceVersion::revision).reversed())
                    .toList();

            _log.info("Found {} version(s) for service '{}' in namespace '{}'",
                    versions.size(), serviceName, namespace);
            return new ServiceVersionHistory(
                    projectId, clusterId, namespace, serviceName, versions.size(), versions);
        } catch (ScalingException ex) {
            throw ex; // preserve context (e.g. cluster not found) from the factory
        } catch (Exception ex) {
            _log.error("Failed to fetch version history for service '{}' in namespace '{}', cluster '{}'",
                    serviceName, namespace, clusterId, ex);
            throw new ScalingException(
                    "Failed to fetch version history for service: " + serviceName, ex);
        }
    }

    /**
     * Reads the Deployment's current active revision from its annotation.
     */
    private long resolveCurrentRevision(AppsV1Api appsApi, String namespace, String serviceName)
            throws Exception {
        var deployment = appsApi.readNamespacedDeployment(serviceName, namespace).execute();
        var annotations = deployment.getMetadata() != null
                ? deployment.getMetadata().getAnnotations() : null;
        return parseRevision(annotations != null ? annotations.get(REVISION_ANNOTATION) : null);
    }

    /**
     * Lists ReplicaSets in the namespace owned by the given Deployment.
     */
    private List<V1ReplicaSet> listOwnedReplicaSets(
            AppsV1Api appsApi, String namespace, String serviceName) throws Exception {
        return appsApi.listNamespacedReplicaSet(namespace).execute().getItems().stream()
                .filter(rs -> isOwnedBy(rs, serviceName))
                .toList();
    }

    /**
     * @return {@code true} if the ReplicaSet is owned by the named Deployment
     */
    private boolean isOwnedBy(V1ReplicaSet replicaSet, String serviceName) {
        var owners = replicaSet.getMetadata() != null
                ? replicaSet.getMetadata().getOwnerReferences() : null;
        if (owners == null) {
            return false;
        }
        return owners.stream().anyMatch(owner ->
                "Deployment".equals(owner.getKind()) && serviceName.equals(owner.getName()));
    }

    private ServiceVersion toServiceVersion(V1ReplicaSet replicaSet, long currentRevision) {
        var metadata = replicaSet.getMetadata();
        var annotations = metadata != null ? metadata.getAnnotations() : null;
        var revision = parseRevision(annotations != null ? annotations.get(REVISION_ANNOTATION) : null);

        return new ServiceVersion(
                revision,
                extractImages(replicaSet),
                desiredReplicas(replicaSet),
                creationTimestamp(replicaSet),
                revision == currentRevision && revision != UNKNOWN_REVISION);
    }

    private List<String> extractImages(V1ReplicaSet replicaSet) {
        var spec = replicaSet.getSpec();
        var template = spec != null ? spec.getTemplate() : null;
        var podSpec = template != null ? template.getSpec() : null;
        if (podSpec == null || podSpec.getContainers() == null) {
            return List.of();
        }
        return podSpec.getContainers().stream()
                .map(container -> container.getImage())
                .filter(image -> image != null)
                .toList();
    }

    private int desiredReplicas(V1ReplicaSet replicaSet) {
        return Optional.ofNullable(replicaSet.getSpec())
                .map(spec -> spec.getReplicas())
                .orElse(0);
    }

    private OffsetDateTime creationTimestamp(V1ReplicaSet replicaSet) {
        return replicaSet.getMetadata() != null
                ? replicaSet.getMetadata().getCreationTimestamp() : null;
    }

    /**
     * Parses the revision annotation, returning {@link #UNKNOWN_REVISION} when
     * missing or malformed.
     */
    private long parseRevision(String rawRevision) {
        if (rawRevision == null || rawRevision.isBlank()) {
            return UNKNOWN_REVISION;
        }
        try {
            return Long.parseLong(rawRevision.trim());
        } catch (NumberFormatException ex) {
            _log.warn("Unparsable revision annotation value '{}'; defaulting to {}",
                    rawRevision, UNKNOWN_REVISION);
            return UNKNOWN_REVISION;
        }
    }
}