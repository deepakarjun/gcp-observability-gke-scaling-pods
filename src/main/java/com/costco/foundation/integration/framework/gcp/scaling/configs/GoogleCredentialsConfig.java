package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;

import java.io.IOException;
import java.util.List;

/**
 * Provides the shared GKE {@link GoogleCredentials} used by all Google Cloud
 * clients (Container, Monitoring, Logging) and, in LOCAL mode, by the
 * Kubernetes API client.
 *
 * <p>Uses Application Default Credentials (ADC), which resolve automatically
 * from {@code gcloud auth application-default login} locally and from Workload
 * Identity / the mounted ServiceAccount when running inside GKE.</p>
 */
@Configuration
public class GoogleCredentialsConfig {

    private static final Logger _log = LoggerFactory.getLogger(GoogleCredentialsConfig.class);

    /** OAuth scope required to call Google Cloud Platform APIs. */
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Shared, auto-refreshing GKE credentials resolved via ADC.
     *
     * @return the scoped {@link GoogleCredentials}
     * @throws IOException if credentials cannot be resolved
     */
    @Bean(name = KubernetesConfig.GKE_CREDENTIALS_BEAN)
    public GoogleCredentials gkeGoogleCredentials() throws IOException {
        try {
            var credentials = GoogleCredentials.getApplicationDefault().createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            
            _log.info("Initialized GKE GoogleCredentials via Application Default Credentials");
            
            return credentials;
        } catch (IOException ex) {
        	
            _log.error("Failed to resolve Application Default Credentials", ex);
            
            throw ex;
        }
    }
    
    /**
     * {@link CoreV1Api} bean built from the shared {@link ApiClient}, used for
     * core resources such as pods and namespaces.
     *
     * @param kubernetesApiClient the configured Kubernetes API client
     * @return a {@link CoreV1Api} instance
     */
    @Bean
    public CoreV1Api coreV1Api(ApiClient kubernetesApiClient) {
    	
        return new CoreV1Api(kubernetesApiClient);
    }
    
    
    /**
     * {@link AppsV1Api} bean built from the shared {@link ApiClient}, used for
     * workload resources such as deployments and replica sets.
     *
     * @param kubernetesApiClient the configured Kubernetes API client
     * @return an {@link AppsV1Api} instance
     */
    @Bean
    public AppsV1Api appsV1Api(ApiClient kubernetesApiClient) {
    	
        return new AppsV1Api(kubernetesApiClient);
    }
    
    /**
     * {@link AutoscalingV1Api} bean built from the shared {@link ApiClient}, used
     * for autoscaling resources such as HorizontalPodAutoscalers.
     *
     * @param kubernetesApiClient the configured Kubernetes API client
     * @return an {@link AutoscalingV1Api} instance
     */
    @Bean
    public AutoscalingV1Api autoscalingV1Api(ApiClient kubernetesApiClient) {
    	
        return new AutoscalingV1Api(kubernetesApiClient);
    }
}