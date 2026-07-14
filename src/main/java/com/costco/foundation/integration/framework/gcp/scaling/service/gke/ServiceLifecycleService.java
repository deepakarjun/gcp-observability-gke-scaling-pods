package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.ResumeRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests.SuspendRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.ServiceStateResponse;

/**
 * Contract for suspending and resuming a service in the cluster.
 */
public interface ServiceLifecycleService {

    ServiceStateResponse suspend(SuspendRequest request);

    ServiceStateResponse resume(ResumeRequest request);
}