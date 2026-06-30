package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ResumeRequest;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.ServiceStateResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.SuspendRequest;

/**
 * Contract for suspending and resuming a service in the cluster.
 */
public interface ServiceLifecycleService {

    ServiceStateResponse suspend(SuspendRequest request);

    ServiceStateResponse resume(ResumeRequest request);
}