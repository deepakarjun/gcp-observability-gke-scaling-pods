package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.KubernetesClientMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kubernetes client configuration bound from application properties.
 *
 * @param clientMode    selects the auth/discovery strategy
 * @param apiBasePath   explicit API server URL (LOCAL mode only)
 * @param caCertBase64  base64-encoded cluster CA certificate (LOCAL mode only)
 * @param caCertPath    filesystem path to a PEM CA certificate (LOCAL mode only)
 */
@ConfigurationProperties(prefix = "kubernetes")
public record KubernetesClientProperties(
        KubernetesClientMode clientMode,
        String apiBasePath,
        String caCertBase64,
        String caCertPath) {

    /** Defaults to LOCAL when unset, matching the developer workflow. */
    public KubernetesClientProperties {
        clientMode = clientMode != null ? clientMode : KubernetesClientMode.LOCAL;
    }
}