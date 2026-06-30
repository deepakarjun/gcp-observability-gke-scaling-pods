package com.costco.foundation.integration.framework.gcp.scaling.configs;


import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;

/**
 * Provides Kubernetes API client beans for dependency injection.
 */
@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws IOException {
    	
    	ApiClient client = Config.defaultClient();

        System.out.println("====================================");
        System.out.println("Base Path : " + client.getBasePath());
//        System.out.println("User Agent: " + client.getUserAgent());
        System.out.println("Auth Names: " + client.getAuthentications().keySet());

        client.getAuthentications().forEach((k,v) -> {
            System.out.println(k + " -> " + v.getClass().getName());
        });

        System.out.println("====================================");
    
        return client;
    }
	
    
//    @Bean
//    public ApiClient apiClient() throws Exception {
//
//        GoogleCredentials credentials =
//                GoogleCredentials.getApplicationDefault()
//                        .createScoped("https://www.googleapis.com/auth/cloud-platform");
//
//        credentials.refreshIfExpired();
//
//        String token = credentials.getAccessToken().getTokenValue();
//
//        ApiClient client = new ClientBuilder()
//                .setBasePath("https://34.55.252.235")
//                .build();
//
//        client.setApiKeyPrefix("Bearer");
//        client.setApiKey(token);
//
//        Configuration.setDefaultApiClient(client);
//
//        return client;
//    }
    
    
//	@Bean
//	public ApiClient apiClient() throws IOException {
//
//	    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
//	                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
//
//	    ApiClient client = new ApiClient();
//
//	    client.setBasePath("https://34.55.252.235");
//
//	    client.setVerifyingSsl(false);   // only if you're currently using this setup
//	                                     // otherwise configure the cluster CA
//
//	    client.setRequestInterceptor(request -> {
//
//	        credentials.refreshIfExpired();
//
//	        String token = credentials
//	                .getAccessToken()
//	                .getTokenValue();
//
//	        request.header("Authorization", "Bearer " + token);
//
//	    });
//
//	    return client;
//	}

    @Bean
    public AppsV1Api appsV1Api(ApiClient apiClient) {
        return new AppsV1Api(apiClient);
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
    	System.out.println("============== Deepak Sharma ======================");
        return new CoreV1Api(apiClient);
    }

    @Bean
    public AutoscalingV1Api autoscalingV1Api(ApiClient apiClient) {
        return new AutoscalingV1Api(apiClient);
    }
}