package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.MinMaxPodResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.PodInfoResponse;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.service.gke.PodInfoService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads running pod counts and min/max replica settings from the cluster.
 */
@Service
public class PodInfoServiceImpl implements PodInfoService {

    private static final Logger _log = LoggerFactory.getLogger(PodInfoServiceImpl.class);
    private static final String RUNNING_PHASE = "Running";
    private static final String HPA_SUFFIX = "-hpa";

    private final CoreV1Api _coreV1Api;
    private final ApiClient _apiClient;
    private final AutoscalingV1Api _autoscalingV1Api;

    public PodInfoServiceImpl(CoreV1Api coreV1Api, ApiClient apiClient,
                              AutoscalingV1Api autoscalingV1Api) {
        _log.info("Kubernetes API Base URL: {}", apiClient.getBasePath());
        this._coreV1Api = coreV1Api;
        this._autoscalingV1Api = autoscalingV1Api;
        this._apiClient = apiClient;
    }

    @Override
    public PodInfoResponse getRunningPods(String projectId, String namespace, String serviceName) {
        try {
            _log.info("Fetching running pods for service '{}' in namespace '{}' (project '{}')",
                    serviceName, namespace, projectId);

            var labelSelector = "app=" + serviceName;

            // v26.0.0 fluent API: namespace is positional, optional params are builder methods
            var pods = _coreV1Api.listNamespacedPod(namespace)
                    .labelSelector(labelSelector)
                    .execute();

            var runningPods = (int) pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null
                            && RUNNING_PHASE.equalsIgnoreCase(pod.getStatus().getPhase()))
                    .count();

            return new PodInfoResponse(projectId, namespace, serviceName, runningPods);
        } catch (ApiException e) {
            _log.error("Failed to fetch running pods for '{}': {}", serviceName, e.getResponseBody(), e);
            throw new ScalingException("Failed to fetch running pods for service: " + serviceName, e);
        }
    }

    @Override
    public MinMaxPodResponse getMinMaxPods(String projectId, String namespace, String serviceName) {
        var hpaName = serviceName + HPA_SUFFIX;
        try {
            _log.info("Fetching min/max pods for service '{}' in namespace '{}' (project '{}')",
                    hpaName, namespace, projectId);

            // v26.0.0 fluent API: name + namespace positional, then .execute()
            var hpa = _autoscalingV1Api
                    .readNamespacedHorizontalPodAutoscaler(hpaName, namespace)
                    .execute();

            var spec = hpa.getSpec();
            if (spec == null) {
                throw new ScalingException("No autoscaler spec found for service: " + serviceName);
            }

            var minPods = spec.getMinReplicas() != null ? spec.getMinReplicas() : 0;
            var maxPods = spec.getMaxReplicas();

            return new MinMaxPodResponse(projectId, namespace, serviceName, minPods, maxPods);
        } catch (ApiException e) {
            _log.error("Failed to fetch min/max pods for '{}': {}", hpaName, e.getResponseBody(), e);
            throw new ScalingException("Failed to fetch min/max pods for service: " + serviceName, e);
        }
    }
}

//package com.costco.foundation.integration.framework.gcp.scaling.service.impl.gke;
//
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.MinMaxPodResponse;
//import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.PodInfoResponse;
//import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
//import com.costco.foundation.integration.framework.gcp.scaling.service.gke.PodInfoService;
//
//import io.kubernetes.client.openapi.ApiClient;
//import io.kubernetes.client.openapi.ApiException;
//import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
//import io.kubernetes.client.openapi.apis.CoreV1Api;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
///**
// * Reads running pod counts and min/max replica settings from the cluster.
// */
//@Service
//public class PodInfoServiceImpl implements PodInfoService {
//
//    private static final Logger _log = LoggerFactory.getLogger(PodInfoServiceImpl.class);
//    private static final String RUNNING_PHASE = "Running";
//
//    private final CoreV1Api _coreV1Api;
//    private final ApiClient _apiClient;
//    private final AutoscalingV1Api _autoscalingV1Api;
//
//    public PodInfoServiceImpl(CoreV1Api coreV1Api, ApiClient apiClient, AutoscalingV1Api autoscalingV1Api) {
//        
//    	_log.info("Kubernetes API Base URL: {}", apiClient.getBasePath());
//    	
//    	this._coreV1Api = coreV1Api;
//        this._autoscalingV1Api = autoscalingV1Api;
//        this._apiClient = apiClient;
//        
////        _log.info("Kubernetes API Base URL: {}", apiClient.getBasePath());
//    }
//
//    @Override
//    public PodInfoResponse getRunningPods(String projectId, String namespace, String serviceName) {
//        try {
//            _log.info("Fetching running pods for service '{}' in namespace '{}' (project '{}')",
//                    serviceName, namespace, projectId);
//
//            var labelSelector = "app=" + serviceName;
//            
//            System.out.println("============== Deepak Sharma ======================");
//            
//            var pods = _coreV1Api.listNamespacedPod(
//                    namespace, null, null, null, null, labelSelector,
//                    null, null, null, null, null, null);
//
//            var runningPods = (int) pods.getItems().stream()
//                    .filter(pod -> pod.getStatus() != null
//                            && RUNNING_PHASE.equalsIgnoreCase(pod.getStatus().getPhase()))
//                    .count();
//
//            return new PodInfoResponse(projectId, namespace, serviceName, runningPods);
//        } catch (ApiException e) {
//            _log.error("Failed to fetch running pods for '{}': {}", serviceName, e.getResponseBody(), e);
//            throw new ScalingException("Failed to fetch running pods for service: " + serviceName, e);
//        }
//    }
//// ...existing code...
//
//    @Override
//    public MinMaxPodResponse getMinMaxPods(String projectId, String namespace, String serviceName) {
//        try {
//            _log.info("Fetching min/max pods for service '{}' in namespace '{}' (project '{}')",
//                    serviceName + "-hpa", namespace, projectId);
//
//            var hpa = _autoscalingV1Api.readNamespacedHorizontalPodAutoscaler(
//                    serviceName + "-hpa", namespace, null);
//            var spec = hpa.getSpec();
//            if (spec == null) {
//                throw new ScalingException("No autoscaler spec found for service: " + serviceName);
//            }
//
//            var minPods = spec.getMinReplicas() != null ? spec.getMinReplicas() : 0;
//            var maxPods = spec.getMaxReplicas();
//
//            return new MinMaxPodResponse(projectId, namespace, serviceName, minPods, maxPods);
//        } catch (ApiException e) {
//            _log.error("Failed to fetch min/max pods for '{}': {}", serviceName, e.getResponseBody(), e);
//            throw new ScalingException("Failed to fetch min/max pods for service: " + serviceName, e);
//        }
//    }
//}