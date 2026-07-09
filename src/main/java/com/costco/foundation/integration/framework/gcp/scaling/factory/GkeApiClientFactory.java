package com.costco.foundation.integration.framework.gcp.scaling.factory;

import com.costco.foundation.integration.framework.gcp.scaling.configs.GoogleAuthInterceptor;
import com.costco.foundation.integration.framework.gcp.scaling.configs.KubernetesConfig;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.container.v1.ClusterManagerClient;
import com.google.container.v1.Cluster;
import com.google.container.v1.ListClustersRequest;
import io.kubernetes.client.openapi.ApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Factory that builds a Kubernetes {@link ApiClient} targeting a specific GKE
 * cluster, resolved dynamically by project and cluster id.
 *
 * <p>Centralizes cluster lookup, endpoint/CA resolution, and attachment of the
 * auto-refreshing {@link GoogleAuthInterceptor} so multiple services can reuse it.</p>
 */
@Component
public class GkeApiClientFactory {

    /** Parent resource for listing clusters across all locations in a project. */
    private static final String CLUSTER_PARENT_TEMPLATE = "projects/%s/locations/-";
    private static final String HTTPS_SCHEME = "https://";

    private final ClusterManagerClient _clusterManagerClient;
    private final GoogleCredentials _credentials;

    public GkeApiClientFactory(
            ClusterManagerClient clusterManagerClient,
            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials) {
        _clusterManagerClient = clusterManagerClient;
        _credentials = gkeGoogleCredentials;
    }

    /**
     * Builds an {@link ApiClient} for the given cluster's API server.
     *
     * @param projectId the GCP project id
     * @param clusterId the cluster name
     * @return a configured {@link ApiClient}
     * @throws ScalingException if the cluster cannot be found
     */
    public ApiClient createApiClient(String projectId, String clusterId) {
        var cluster = findCluster(projectId, clusterId);
        return buildApiClient(cluster);
    }

    private Cluster findCluster(String projectId, String clusterId) {
        var parent = CLUSTER_PARENT_TEMPLATE.formatted(projectId);
        var request = ListClustersRequest.newBuilder().setParent(parent).build();
        return _clusterManagerClient.listClusters(request).getClustersList().stream()
                .filter(cluster -> cluster.getName().equals(clusterId))
                .findFirst()
                .orElseThrow(() -> new ScalingException(
                        "Cluster not found: " + clusterId + " in project: " + projectId));
    }

    private ApiClient buildApiClient(Cluster cluster) {
        var caCert = Base64.getDecoder()
                .decode(cluster.getMasterAuth().getClusterCaCertificate());

        var apiClient = new ApiClient();
        apiClient.setBasePath(HTTPS_SCHEME + cluster.getEndpoint());
        apiClient.setSslCaCert(new ByteArrayInputStream(caCert));

        var httpClient = apiClient.getHttpClient().newBuilder()
                .addInterceptor(new GoogleAuthInterceptor(_credentials))
                .build();
        apiClient.setHttpClient(httpClient);
        return apiClient;
    }
}