package com.costco.foundation.integration.framework.gcp.scaling.service.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.MinMaxPodResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses.PodInfoResponse;

/**
 * Contract for retrieving pod information for a service in a cluster.
 */
public interface PodInfoService {

    PodInfoResponse getRunningPods(String projectId, String namespace, String serviceName);

    MinMaxPodResponse getMinMaxPods(String projectId, String namespace, String serviceName, String HpaName);
}