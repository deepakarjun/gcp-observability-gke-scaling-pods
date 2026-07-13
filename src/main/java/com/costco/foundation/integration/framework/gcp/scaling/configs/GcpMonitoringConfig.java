package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Provides the Cloud Monitoring client used to read pre-aggregated
 * log-based metrics (avoids Logging read-quota limits).
 */
@Configuration
public class GcpMonitoringConfig {

    /**
     * Cloud Monitoring client wired with the shared GKE credentials so it
     * presents the same service-account identity as the rest of the app.
     *
     * @param gkeGoogleCredentials the shared, auto-refreshing credentials
     * @return a configured {@link MetricServiceClient}
     * @throws IOException if the client cannot be initialized
     */
    @Bean(destroyMethod = "close")
    public MetricServiceClient metricServiceClient(
            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials)
            throws IOException {
        var settings = MetricServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(gkeGoogleCredentials))
                .build();
        return MetricServiceClient.create(settings);
    }
}