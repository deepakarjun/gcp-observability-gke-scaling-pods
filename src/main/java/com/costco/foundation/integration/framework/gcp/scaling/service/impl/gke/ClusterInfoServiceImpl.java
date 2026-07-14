package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.KeyPointsProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterInfo;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceInfo;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterInfoService;
import com.google.cloud.container.v1.ClusterManagerClient;
import com.google.container.v1.Cluster;
import com.google.container.v1.ListClustersRequest;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation that discovers clusters via the GKE Container API and
 * lists user-defined namespaces (matched by configured prefixes) by delegating
 * cluster/API-client resolution to {@link GkeApiClientFactory}.
 */
@Service
public class ClusterInfoServiceImpl implements ClusterInfoService {

    private static final Logger _log = LoggerFactory.getLogger(ClusterInfoServiceImpl.class);

    /** Parent resource for listing clusters across all locations in a project. */
    private static final String CLUSTER_PARENT_TEMPLATE = "projects/%s/locations/-";

    private final ClusterManagerClient _clusterManagerClient;
    private final GkeApiClientFactory _apiClientFactory;
    private final KeyPointsProperties _keyPointsProperties;

    public ClusterInfoServiceImpl(
            ClusterManagerClient clusterManagerClient,
            GkeApiClientFactory apiClientFactory,
            KeyPointsProperties keyPointsProperties) {
        _clusterManagerClient = clusterManagerClient;
        _apiClientFactory = apiClientFactory;
        _keyPointsProperties = keyPointsProperties;
    }

    @Override
    public ClusterListResponse getClusters(String projectId) {
        _log.info("Fetching clusters for project '{}'", projectId);
        try {
            var clusters = listClusters(projectId).stream()
                    .map(this::toClusterInfo)
                    .toList();

            _log.info("Found {} cluster(s) in project '{}'", clusters.size(), projectId);
            return new ClusterListResponse(projectId, clusters.size(), clusters);
        } catch (Exception ex) {
            _log.error("Failed to fetch clusters for project '{}'", projectId, ex);
            throw new ScalingException("Failed to fetch clusters for project: " + projectId, ex);
        }
    }

    @Override
    public NamespaceListResponse getUserNamespaces(String projectId, String clusterId) {
        _log.info("Fetching user namespaces for cluster '{}' in project '{}'", clusterId, projectId);
        try {
            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
            var coreApi = new CoreV1Api(apiClient);

            var prefixes = _keyPointsProperties.namespacePrefixes();
            if (prefixes.isEmpty()) {
                _log.warn("No namespace prefixes configured; returning no namespaces for cluster '{}'",
                        clusterId);
                return new NamespaceListResponse(projectId, clusterId, 0, List.of());
            }

            var namespaceList = coreApi.listNamespace().execute();
            var namespaces = namespaceList.getItems().stream()
                    .filter(ns -> ns.getMetadata() != null
                            && matchesConfiguredPrefix(ns.getMetadata().getName(), prefixes))
                    .map(this::toNamespaceInfo)
                    .toList();

            _log.info("Found {} user namespace(s) in cluster '{}'", namespaces.size(), clusterId);
            return new NamespaceListResponse(projectId, clusterId, namespaces.size(), namespaces);
        } catch (ScalingException ex) {
            throw ex; // preserve "cluster not found" context from the factory
        } catch (Exception ex) {
            _log.error("Failed to fetch namespaces for cluster '{}' in project '{}'", clusterId, projectId, ex);
            throw new ScalingException("Failed to fetch namespaces for cluster: " + clusterId, ex);
        }
    }

    private List<Cluster> listClusters(String projectId) {
        var parent = CLUSTER_PARENT_TEMPLATE.formatted(projectId);
        var request = ListClustersRequest.newBuilder().setParent(parent).build();
        return _clusterManagerClient.listClusters(request).getClustersList();
    }

    private ClusterInfo toClusterInfo(Cluster cluster) {
        return new ClusterInfo(
                cluster.getName(),
                cluster.getLocation(),
                cluster.getStatus().name());
    }

    private NamespaceInfo toNamespaceInfo(V1Namespace ns) {
        var status = ns.getStatus() != null ? ns.getStatus().getPhase() : null;
        return new NamespaceInfo(ns.getMetadata().getName(), status);
    }

    /**
     * @return {@code true} if the namespace name starts with any configured prefix
     */
    private boolean matchesConfiguredPrefix(String name, List<String> prefixes) {
        return prefixes.stream().anyMatch(name::startsWith);
    }
}

//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterInfo;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterListResponse;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceInfo;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceListResponse;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.factory.GkeApiClientFactory;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterInfoService;
//import com.google.cloud.container.v1.ClusterManagerClient;
//import com.google.container.v1.Cluster;
//import com.google.container.v1.ListClustersRequest;
//import io.kubernetes.client.openapi.apis.CoreV1Api;
//import io.kubernetes.client.openapi.models.V1Namespace;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//
///**
// * Default implementation that discovers clusters via the GKE Container API and
// * lists user-created namespaces by delegating cluster/API-client resolution to
// * {@link GkeApiClientFactory}.
// */
//@Service
//public class ClusterInfoServiceImpl implements ClusterInfoService {
//
//    private static final Logger _log = LoggerFactory.getLogger(ClusterInfoServiceImpl.class);
//
//    /** Parent resource for listing clusters across all locations in a project. */
//    private static final String CLUSTER_PARENT_TEMPLATE = "projects/%s/locations/-";
//
//    /** Namespaces provisioned by Kubernetes/GKE, excluded from "user" namespaces. */
//    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
//            "default",
//            "kube-system",
//            "kube-public",
//            "kube-node-lease",
//            "gke-gmp-system",
//            "gmp-system");
//
//    /** Prefixes for GKE-managed namespaces, also excluded. */
//    private static final List<String> SYSTEM_NAMESPACE_PREFIXES = List.of("gke-managed-");
//
//    private final ClusterManagerClient _clusterManagerClient;
//    private final GkeApiClientFactory _apiClientFactory;
//
//    public ClusterInfoServiceImpl(
//            ClusterManagerClient clusterManagerClient,
//            GkeApiClientFactory apiClientFactory) {
//        _clusterManagerClient = clusterManagerClient;
//        _apiClientFactory = apiClientFactory;
//    }
//
//    @Override
//    public ClusterListResponse getClusters(String projectId) {
//        _log.info("Fetching clusters for project '{}'", projectId);
//        try {
//            var clusters = listClusters(projectId).stream()
//                    .map(this::toClusterInfo)
//                    .toList();
//
//            _log.info("Found {} cluster(s) in project '{}'", clusters.size(), projectId);
//            return new ClusterListResponse(projectId, clusters.size(), clusters);
//        } catch (Exception ex) {
//            _log.error("Failed to fetch clusters for project '{}'", projectId, ex);
//            throw new ScalingException("Failed to fetch clusters for project: " + projectId, ex);
//        }
//    }
//
//    @Override
//    public NamespaceListResponse getUserNamespaces(String projectId, String clusterId) {
//        _log.info("Fetching user namespaces for cluster '{}' in project '{}'", clusterId, projectId);
//        try {
//            var apiClient = _apiClientFactory.createApiClient(projectId, clusterId);
//            var coreApi = new CoreV1Api(apiClient);
//
//            var namespaceList = coreApi.listNamespace().execute();
//            var namespaces = namespaceList.getItems().stream()
//                    .filter(ns -> ns.getMetadata() != null && isUserNamespace(ns.getMetadata().getName()))
//                    .map(this::toNamespaceInfo)
//                    .toList();
//
//            _log.info("Found {} user namespace(s) in cluster '{}'", namespaces.size(), clusterId);
//            return new NamespaceListResponse(projectId, clusterId, namespaces.size(), namespaces);
//        } catch (ScalingException ex) {
//            throw ex; // preserve "cluster not found" context from the factory
//        } catch (Exception ex) {
//            _log.error("Failed to fetch namespaces for cluster '{}' in project '{}'", clusterId, projectId, ex);
//            throw new ScalingException("Failed to fetch namespaces for cluster: " + clusterId, ex);
//        }
//    }
//
//    private List<Cluster> listClusters(String projectId) {
//        var parent = CLUSTER_PARENT_TEMPLATE.formatted(projectId);
//        var request = ListClustersRequest.newBuilder().setParent(parent).build();
//        return _clusterManagerClient.listClusters(request).getClustersList();
//    }
//
//    private ClusterInfo toClusterInfo(Cluster cluster) {
//        return new ClusterInfo(
//                cluster.getName(),
//                cluster.getLocation(),
//                cluster.getStatus().name());
//    }
//
//    private NamespaceInfo toNamespaceInfo(V1Namespace ns) {
//        var status = ns.getStatus() != null ? ns.getStatus().getPhase() : null;
//        return new NamespaceInfo(ns.getMetadata().getName(), status);
//    }
//
//    private boolean isUserNamespace(String name) {
//        if (SYSTEM_NAMESPACES.contains(name)) {
//            return false;
//        }
//        return SYSTEM_NAMESPACE_PREFIXES.stream().noneMatch(name::startsWith);
//    }
//}
