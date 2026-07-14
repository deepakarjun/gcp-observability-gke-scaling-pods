
package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ScaleToReplicasRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ScaleToReplicasResponse;

/**
 * Contract for scaling a service to a specific replica count within HPA bounds.
 */
public interface ReplicaScalingService {

    ScaleToReplicasResponse scaleTo(ScaleToReplicasRequest request);
}