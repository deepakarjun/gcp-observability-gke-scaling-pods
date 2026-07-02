package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.auth.oauth2.GoogleCredentials;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

/**
 * Provides Kubernetes API client beans for dependency injection.
 *
 * <p>Authentication is delegated to {@link GoogleAuthInterceptor}, which injects
 * a freshly refreshed Google access token on every request so the client keeps
 * working after the initial GKE token expires.</p>
 */
@Configuration
public class KubernetesConfig {

    private static final Logger _log = LoggerFactory.getLogger(KubernetesConfig.class);

    /**
     * Custom bean name to avoid clashing with the {@code googleCredentials} bean
     * auto-configured by Spring Cloud GCP ({@code GcpContextAutoConfiguration}).
     */
    private static final String GKE_CREDENTIALS_BEAN = "gkeGoogleCredentials";
    
    /** Grants full Cloud Platform API access for GKE control-plane calls. */
    private static final String SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Ensures the access token carries the caller's email. Without this scope, GKE
     * identifies the service account by its numeric uniqueId instead of its email.
     */
    private static final String SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email";
    
    private static final List<String> GKE_SCOPES = List.of(SCOPE_CLOUD_PLATFORM, SCOPE_USERINFO_EMAIL);

    /**
     * Application Default Credentials scoped for GKE access. These credentials
     * are auto-refreshing; the interceptor calls {@code refreshIfExpired()} per request.
     */
    @Bean(GKE_CREDENTIALS_BEAN)
    public GoogleCredentials gkeGoogleCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault().createScoped(GKE_SCOPES);
    }

    /**
     * Builds the API client using the cluster base path and TLS settings from the
     * default config, but replaces static token auth with a per-request refreshing
     * interceptor.
     */
    @Bean
    public ApiClient apiClient( @Qualifier(GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials ) throws IOException {
        var client = Config.defaultClient();

        var httpClient = client.getHttpClient().newBuilder().addInterceptor(new GoogleAuthInterceptor(gkeGoogleCredentials)).build();
        client.setHttpClient(httpClient);

        _log.info("Kubernetes ApiClient initialized. Base Path: {}", client.getBasePath());
        return client;
    }
    
    @Bean
    public AppsV1Api appsV1Api(ApiClient apiClient) {
        return new AppsV1Api(apiClient);
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public AutoscalingV1Api autoscalingV1Api(ApiClient apiClient) {
        return new AutoscalingV1Api(apiClient);
    }
}
