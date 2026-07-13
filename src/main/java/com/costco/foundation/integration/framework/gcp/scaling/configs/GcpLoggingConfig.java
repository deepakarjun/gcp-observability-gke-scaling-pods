package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Cloud Logging client used to query log-severity counts.
 */
@Configuration
public class GcpLoggingConfig {

    /**
     * Cloud Logging client wired with the shared GKE credentials so it presents
     * the same service-account identity as the rest of the application.
     *
     * @param gkeGoogleCredentials the shared, auto-refreshing credentials
     * @return a configured {@link Logging} client
     */
    @Bean(destroyMethod = "close")
    public Logging logging(
            @Qualifier(KubernetesConfig.GKE_CREDENTIALS_BEAN) GoogleCredentials gkeGoogleCredentials) {
        return LoggingOptions.newBuilder()
                .setCredentials(gkeGoogleCredentials)
                .build()
                .getService();
    }
}