package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.KubernetesClientMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kubernetes client configuration bound from application properties.
 *
 * @param clientMode  selects the auth/discovery strategy
 * @param apiBasePath explicit API server URL (used only in {@code LOCAL} mode)
 */
@ConfigurationProperties(prefix = "kubernetes")
public record KubernetesClientProperties(
        KubernetesClientMode clientMode,
        String apiBasePath) {

    /** Defaults to LOCAL when unset, matching the developer workflow. */
    public KubernetesClientProperties {
        clientMode = clientMode != null ? clientMode : KubernetesClientMode.LOCAL;
    }
}