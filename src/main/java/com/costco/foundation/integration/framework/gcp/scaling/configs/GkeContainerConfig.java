package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.container.v1.ClusterManagerClient;
import com.google.cloud.container.v1.ClusterManagerSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Provides the GKE Container API client used to discover clusters within a
 * project.
 */
@Configuration
public class GkeContainerConfig {

    /**
     * {@link ClusterManagerClient} wired with the shared GKE credentials so it
     * presents the same service-account identity as the rest of the app.
     * Spring closes the client automatically on context shutdown.
     *
     * @param gkeGoogleCredentials the shared, auto-refreshing credentials
     * @return a configured {@link ClusterManagerClient}
     * @throws IOException if the client cannot be initialized
     */
    @Bean(destroyMethod = "close")
    public ClusterManagerClient clusterManagerClient(
            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials)
            throws IOException {
        var settings = ClusterManagerSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(gkeGoogleCredentials))
                .build();
        return ClusterManagerClient.create(settings);
    }
}