package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

public enum KubernetesClientMode {

	/** Local development: uses GKE {@code GoogleCredentials} and an explicit API base path. */
    LOCAL,

    /** Running inside a GKE pod: uses the mounted ServiceAccount token and CA. */
    IN_CLUSTER
}
