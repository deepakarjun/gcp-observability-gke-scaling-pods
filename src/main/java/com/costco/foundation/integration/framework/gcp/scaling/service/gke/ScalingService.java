package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ScaleRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ScaleResponse;

/**
 * Contract for GKE scaling operations.
 */
public interface ScalingService {

    ScaleResponse scale(ScaleRequest request);
}