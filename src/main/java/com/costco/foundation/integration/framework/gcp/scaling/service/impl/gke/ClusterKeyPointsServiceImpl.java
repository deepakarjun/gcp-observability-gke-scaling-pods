package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.KeyPointsProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ClusterKeyPoints;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.PodPhase;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterKeyPointsService;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.LogSeverityService;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation that aggregates cluster-wide key points by iterating
 * over user-defined namespaces (matched by configured prefixes) and tallying
 * deployments and pods.
 */
@Service
public class ClusterKeyPointsServiceImpl implements ClusterKeyPointsService {

    private static final Logger _log = LoggerFactory.getLogger(ClusterKeyPointsServiceImpl.class);

    /** A deployment is considered suspended when its desired replica count is this value. */
    private static final int SUSPENDED_REPLICA_COUNT = 0;

    private final GkeApiClientFactory _apiClientFactory;
    private final LogSeverityService _logSeverityService;
    private final KeyPointsProperties _keyPointsProperties;

    public ClusterKeyPointsServiceImpl(
            GkeApiClientFactory apiClientFactory,
            LogSeverityService logSeverityService,
            KeyPointsProperties keyPointsProperties) {
        _apiClientFactory = apiClientFactory;
        _logSeverityService = logSeverityService;
        _keyPointsProperties = keyPointsProperties;
    }

    @Override
    public ClusterKeyPoints getKeyPoints(String projectId, String clusterId) {
        _log.info("Computing key points for cluster '{}' in project '{}'", clusterId, projectId);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var coreApi = new CoreV1Api(apiClient);
            var appsApi = new AppsV1Api(apiClient);

            var userNamespaces = listUserNamespaces(coreApi);
            var accumulator = new KeyPointsAccumulator();

            for (var namespace : userNamespaces) {
                tallyDeployments(appsApi, namespace, accumulator);
                tallyPods(coreApi, namespace, accumulator);
            }

            var severityCounts =
                    _logSeverityService.getWeeklySeverityCounts(projectId, clusterId, userNamespaces);

            var keyPoints = new ClusterKeyPoints(
                    projectId,
                    clusterId,
                    userNamespaces.size(),
                    accumulator.servicesRunning,
                    accumulator.servicesSuspended,
                    accumulator.activePods,
                    accumulator.containers,
                    severityCounts);

            _log.info("Key points for cluster '{}': {}", clusterId, keyPoints);
            return keyPoints;
        } catch (ScalingException ex) {
            throw ex; // preserve "cluster not found" context from the factory
        } catch (Exception ex) {
            _log.error("Failed to compute key points for cluster '{}' in project '{}'",
                    clusterId, projectId, ex);
            throw new ScalingException("Failed to compute key points for cluster: " + clusterId, ex);
        }
    }

    /**
     * Lists namespaces whose names match one of the configured user-defined
     * prefixes. If no prefixes are configured, an empty list is returned.
     */
    private List<String> listUserNamespaces(CoreV1Api coreApi) throws Exception {
        var prefixes = _keyPointsProperties.namespacePrefixes();
        if (prefixes.isEmpty()) {
            _log.warn("No Key Points namespace prefixes configured; no namespaces will be counted");
            return List.of();
        }
        return coreApi.listNamespace().execute().getItems().stream()
                .filter(ns -> ns.getMetadata() != null)
                .map(ns -> ns.getMetadata().getName())
                .filter(name -> matchesConfiguredPrefix(name, prefixes))
                .toList();
    }

    /**
     * @return {@code true} if the namespace name starts with any configured prefix
     */
    private boolean matchesConfiguredPrefix(String name, List<String> prefixes) {
        return prefixes.stream().anyMatch(name::startsWith);
    }

    private void tallyDeployments(AppsV1Api appsApi, String namespace, KeyPointsAccumulator acc)
            throws Exception {
        var deployments = appsApi.listNamespacedDeployment(namespace).execute().getItems();
        for (var deployment : deployments) {
            if (isSuspended(deployment)) {
                acc.servicesSuspended++;
            } else {
                acc.servicesRunning++;
            }
        }
    }

    private void tallyPods(CoreV1Api coreApi, String namespace, KeyPointsAccumulator acc)
            throws Exception {
        var pods = coreApi.listNamespacedPod(namespace).execute().getItems();
        for (var pod : pods) {
            if (isRunning(pod)) {
                acc.activePods++;
                acc.containers += containerCount(pod);
            }
        }
    }

    private boolean isSuspended(V1Deployment deployment) {
        var replicas = deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null;
        return replicas != null && replicas == SUSPENDED_REPLICA_COUNT;
    }

    private boolean isRunning(V1Pod pod) {
        var phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
        return PodPhase.RUNNING.value().equals(phase);
    }

    private int containerCount(V1Pod pod) {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) {
            return 0;
        }
        return pod.getSpec().getContainers().size();
    }

    /** Mutable tally holder used while aggregating across namespaces. */
    private static final class KeyPointsAccumulator {
        private int servicesRunning;
        private int servicesSuspended;
        private int activePods;
        private int containers;
    }
}
