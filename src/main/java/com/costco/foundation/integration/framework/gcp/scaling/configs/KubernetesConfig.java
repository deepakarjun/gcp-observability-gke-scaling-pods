package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.KubernetesClientMode;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.google.auth.oauth2.GoogleCredentials;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Builds the Kubernetes {@link ApiClient} using a mode-driven strategy so the
 * same service runs both locally (GKE credentials + explicit endpoint) and
 * inside a GKE pod (mounted ServiceAccount token via in-cluster config).
 */
@Configuration
public class KubernetesConfig {

    private static final Logger _log = LoggerFactory.getLogger(KubernetesConfig.class);

    /** Bean qualifier for the shared GKE credentials. */
    public static final String GKE_CREDENTIALS_BEAN = "gkeGoogleCredentials";

    private final KubernetesClientProperties _properties;

    public KubernetesConfig(KubernetesClientProperties properties) {
        _properties = properties;
    }

    /**
     * Primary Kubernetes {@link ApiClient}, constructed per the configured mode.
     *
     * @param gkeGoogleCredentials shared credentials (used only in LOCAL mode)
     * @return a configured {@link ApiClient}
     */
    @Bean
    public ApiClient kubernetesApiClient( @Qualifier(GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials) {
    	
        var mode = _properties.clientMode();
        _log.info("Initializing Kubernetes ApiClient in '{}' mode", mode);

        var apiClient = switch (mode) {
            case IN_CLUSTER -> buildInClusterClient();
            case LOCAL -> buildLocalClient(gkeGoogleCredentials);
        };

        _log.info("Kubernetes ApiClient initialized. Base Path: {}", apiClient.getBasePath());
        return apiClient;
    }

    /**
     * In-cluster client: auto-discovers API server address, CA, and the mounted
     * ServiceAccount token. No base path or interceptor is set manually.
     */
    private ApiClient buildInClusterClient() {
        try {
            _log.info("Using in-cluster configuration (mounted ServiceAccount)");
            return ClientBuilder.cluster().build();
        } catch (Exception ex) {
            _log.error("Failed to initialize in-cluster Kubernetes ApiClient", ex);
            throw new ScalingException("Failed to initialize in-cluster Kubernetes client", ex);
        }
    }

    /**
     * Local client: uses the configured API base path and attaches the
     * auto-refreshing {@link GoogleAuthInterceptor} for GKE authentication.
     */
//    private ApiClient buildLocalClient(GoogleCredentials gkeGoogleCredentials) {
//        var basePath = _properties.apiBasePath();
//        if (basePath == null || basePath.isBlank()) {
//            throw new ScalingException(
//                    "kubernetes.api-base-path must be set when client-mode is LOCAL");
//        }
//        _log.info("Using local configuration with API base path '{}'", basePath);
//
//        var apiClient = new ApiClient();
//        apiClient.setBasePath(basePath);
//
//        var httpClient = apiClient.getHttpClient().newBuilder()
//                .addInterceptor(new GoogleAuthInterceptor(gkeGoogleCredentials))
//                .build();
//        apiClient.setHttpClient(httpClient);
//        return apiClient;
//    }
    
    /**
     * Local client: uses the configured API base path, attaches the
     * auto-refreshing {@link GoogleAuthInterceptor}, and trusts the cluster CA
     * certificate to satisfy TLS validation.
     */
    private ApiClient buildLocalClient(GoogleCredentials gkeGoogleCredentials) {
        var basePath = _properties.apiBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new ScalingException(
                    "kubernetes.api-base-path must be set when client-mode is LOCAL");
        }
        _log.info("Using local configuration with API base path '{}'", basePath);

        var apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        applyCaCert(apiClient);

        var httpClient = apiClient.getHttpClient().newBuilder()
                .addInterceptor(new GoogleAuthInterceptor(gkeGoogleCredentials))
                .build();
        apiClient.setHttpClient(httpClient);
        return apiClient;
    }
    
    
    /**
     * Loads the cluster CA certificate from configuration (base64 preferred,
     * else file path) and applies it to the client so TLS validation succeeds
     * against the GKE private CA.
     */
    private void applyCaCert(ApiClient apiClient) {
        try {
            var caCertBytes = resolveCaCertBytes();
            if (caCertBytes == null) {
                _log.warn("No CA cert configured; TLS validation may fail against the cluster");
                return;
            }
            apiClient.setSslCaCert(new ByteArrayInputStream(caCertBytes));
            _log.info("Applied cluster CA certificate to Kubernetes ApiClient");
        } catch (Exception ex) {
            _log.error("Failed to apply cluster CA certificate", ex);
            throw new ScalingException("Failed to apply cluster CA certificate", ex);
        }
    }
    
    /**
     * @return the CA certificate bytes from base64 config, else from the file
     *         path, else {@code null} if neither is configured
     */
    private byte[] resolveCaCertBytes() throws Exception {
        var base64 = _properties.caCertBase64();
        if (base64 != null && !base64.isBlank()) {
            return Base64.getDecoder().decode(base64.trim());
        }
        var path = _properties.caCertPath();
        if (path != null && !path.isBlank()) {
            return Files.readAllBytes(Path.of(path));
        }
        return null;
    }
    
}


//package com.costco.foundation.integration.framework.gcp.scaling.configs;
//
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.cloud.container.v1.ClusterManagerClient;
//import com.google.cloud.container.v1.ClusterManagerSettings;
//import com.google.api.gax.core.FixedCredentialsProvider;
//import io.kubernetes.client.openapi.ApiClient;
//import io.kubernetes.client.openapi.apis.AppsV1Api;
//import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
//import io.kubernetes.client.openapi.apis.CoreV1Api;
//import io.kubernetes.client.util.Config;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.io.IOException;
//import java.security.NoSuchAlgorithmException;
//import java.util.List;
//
///**
// * Provides Kubernetes API client beans for dependency injection.
// *
// * <p>Authentication is delegated to {@link GoogleAuthInterceptor}, which injects
// * a freshly refreshed Google access token on every request so the client keeps
// * working after the initial GKE token expires.</p>
// */
//@Configuration
//public class KubernetesConfig {
//
//    private static final Logger _log = LoggerFactory.getLogger(KubernetesConfig.class);
//
//    /**
//     * Custom bean name to avoid clashing with the {@code googleCredentials} bean
//     * auto-configured by Spring Cloud GCP ({@code GcpContextAutoConfiguration}).
//     */
//    public static final String GKE_CREDENTIALS_BEAN = "gkeGoogleCredentials";
//    
//    /** Grants full Cloud Platform API access for GKE control-plane calls. */
//    private static final String SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";
//
//    /**
//     * Ensures the access token carries the caller's email. Without this scope, GKE
//     * identifies the service account by its numeric uniqueId instead of its email.
//     */
//    private static final String SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email";
//    
//    private static final List<String> GKE_SCOPES = List.of(SCOPE_CLOUD_PLATFORM, SCOPE_USERINFO_EMAIL);
//
//    /**
//     * Application Default Credentials scoped for GKE access. These credentials
//     * are auto-refreshing; the interceptor calls {@code refreshIfExpired()} per request.
//     */
//    @Bean(GKE_CREDENTIALS_BEAN)
//    public GoogleCredentials gkeGoogleCredentials() throws IOException {
//    	
//    	
//    	System.out.println("Java Version : " + System.getProperty("java.version"));
//    	System.out.println("Java Vendor  : " + System.getProperty("java.vendor"));
//    	try {
//			System.out.println("TLS Provider : " + javax.net.ssl.SSLContext.getDefault().getProvider());
//		} catch (NoSuchAlgorithmException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//    	try {
//			System.out.println("ALPN Supported : " + javax.net.ssl.SSLContext.getDefault().getProtocol());
//		} catch (NoSuchAlgorithmException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//    	    	
//        return GoogleCredentials.getApplicationDefault().createScoped(GKE_SCOPES);
//    }
//
//    /**
//     * Builds the API client using the cluster base path and TLS settings from the
//     * default config, but replaces static token auth with a per-request refreshing
//     * interceptor.
//     */
//    @Bean
//    public ApiClient apiClient( @Qualifier(GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials ) throws IOException {
//        var client = Config.defaultClient();
//
//        var httpClient = client.getHttpClient().newBuilder().addInterceptor(new GoogleAuthInterceptor(gkeGoogleCredentials)).build();
//        client.setHttpClient(httpClient);
//
//        _log.info("Kubernetes ApiClient initialized. Base Path: {}", client.getBasePath());
//        return client;
//    }
//    
//    @Bean
//    public AppsV1Api appsV1Api(ApiClient apiClient) {
//        return new AppsV1Api(apiClient);
//    }
//
//    @Bean
//    public CoreV1Api coreV1Api(ApiClient apiClient) {
//        return new CoreV1Api(apiClient);
//    }
//
//    @Bean
//    public AutoscalingV1Api autoscalingV1Api(ApiClient apiClient) {
//        return new AutoscalingV1Api(apiClient);
//    }
//    
//    /**
//     * Client for the GKE Container API. Spring closes it automatically on shutdown.
//     *
//     * @param gkeGoogleCredentials the shared, auto-refreshing GKE credentials
//     * @return a configured {@link ClusterManagerClient}
//     * @throws IOException if the client cannot be initialized
//     */
//    @Bean(destroyMethod = "close")
//    public ClusterManagerClient clusterManagerClient(
//            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials)
//            throws IOException {
//        var settings = ClusterManagerSettings.newBuilder()
//                .setCredentialsProvider(FixedCredentialsProvider.create(gkeGoogleCredentials))
//                .build();
//        return ClusterManagerClient.create(settings);
//    }
//}
