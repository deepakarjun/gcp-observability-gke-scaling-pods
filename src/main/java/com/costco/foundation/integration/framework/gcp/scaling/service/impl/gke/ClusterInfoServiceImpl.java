package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.configs.GoogleAuthInterceptor;
import com.costco.foundation.integration.framework.gcp.scaling.configs.KubernetesConfig;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterInfo;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ClusterListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceInfo;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.NamespaceListResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.ClusterInfoService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.container.v1.ClusterManagerClient;
import com.google.container.v1.Cluster;
import com.google.container.v1.ListClustersRequest;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Default implementation that discovers clusters via the GKE Container API and
 * lists user-created namespaces by connecting to a target cluster's API server.
 */
@Service
public class ClusterInfoServiceImpl implements ClusterInfoService {

    private static final Logger _log = LoggerFactory.getLogger(ClusterInfoServiceImpl.class);

    /** Parent resource for listing clusters across all locations in a project. */
    private static final String CLUSTER_PARENT_TEMPLATE = "projects/%s/locations/-";
    private static final String HTTPS_SCHEME = "https://";

    /** Namespaces provisioned by Kubernetes/GKE, excluded from "user" namespaces. */
    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "default",
            "kube-system",
            "kube-public",
            "kube-node-lease",
            "gke-gmp-system",
            "gmp-system");

    /** Prefixes for GKE-managed namespaces, also excluded. */
    private static final List<String> SYSTEM_NAMESPACE_PREFIXES = List.of("gke-managed-");

    private final ClusterManagerClient _clusterManagerClient;
    private final GoogleCredentials _credentials;

    public ClusterInfoServiceImpl(
            ClusterManagerClient clusterManagerClient,
            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials) {
        _clusterManagerClient = clusterManagerClient;
        _credentials = gkeGoogleCredentials;
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
            var cluster = findCluster(projectId, clusterId);
            var namespaces = fetchUserNamespaces(cluster);

            _log.info("Found {} user namespace(s) in cluster '{}'", namespaces.size(), clusterId);
            return new NamespaceListResponse(projectId, clusterId, namespaces.size(), namespaces);
        } catch (ScalingException ex) {
            throw ex; // preserve "cluster not found" and other contextual errors
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

    private Cluster findCluster(String projectId, String clusterId) {
        return listClusters(projectId).stream()
                .filter(cluster -> cluster.getName().equals(clusterId))
                .findFirst()
                .orElseThrow(() -> new ScalingException(
                        "Cluster not found: " + clusterId + " in project: " + projectId));
    }

    private ClusterInfo toClusterInfo(Cluster cluster) {
        return new ClusterInfo(
                cluster.getName(),
                cluster.getLocation(),
                cluster.getStatus().name());
    }

    /**
     * Connects to the cluster's API server and returns its user namespaces.
     * Returns an empty list if the cluster is not in a queryable state.
     */
    private List<NamespaceInfo> fetchUserNamespaces(Cluster cluster) throws ApiException, IOException {
        if (cluster.getStatus() != Cluster.Status.RUNNING || cluster.getEndpoint().isBlank()) {
            _log.warn("Cluster '{}' not ready for namespace fetch (status={})",
                    cluster.getName(), cluster.getStatus());
            return List.of();
        }

        var coreApi = buildCoreApi(cluster);
        var namespaceList = coreApi.listNamespace().execute();

        return namespaceList.getItems().stream()
                .filter(ns -> ns.getMetadata() != null && isUserNamespace(ns.getMetadata().getName()))
                .map(this::toNamespaceInfo)
                .toList();
    }

    private NamespaceInfo toNamespaceInfo(V1Namespace ns) {
        var status = ns.getStatus() != null ? ns.getStatus().getPhase() : null;
        return new NamespaceInfo(ns.getMetadata().getName(), status);
    }

    /**
     * Builds a per-cluster {@link CoreV1Api} using the cluster endpoint and CA cert,
     * authenticated with the auto-refreshing {@link GoogleAuthInterceptor}.
     */
    private CoreV1Api buildCoreApi(Cluster cluster) {
        var caCert = Base64.getDecoder()
                .decode(cluster.getMasterAuth().getClusterCaCertificate());

        var apiClient = new ApiClient();
        apiClient.setBasePath(HTTPS_SCHEME + cluster.getEndpoint());
        apiClient.setSslCaCert(new ByteArrayInputStream(caCert));

        var httpClient = apiClient.getHttpClient().newBuilder()
                .addInterceptor(new GoogleAuthInterceptor(_credentials))
                .build();
        apiClient.setHttpClient(httpClient);

        return new CoreV1Api(apiClient);
    }

    private boolean isUserNamespace(String name) {
        if (SYSTEM_NAMESPACES.contains(name)) {
            return false;
        }
        return SYSTEM_NAMESPACE_PREFIXES.stream().noneMatch(name::startsWith);
    }
}