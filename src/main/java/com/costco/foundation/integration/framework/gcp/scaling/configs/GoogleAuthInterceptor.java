package com.costco.foundation.integration.framework.gcp.scaling.configs;

import com.google.auth.oauth2.GoogleCredentials;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * OkHttp interceptor that injects a freshly refreshed Google access token into
 * every Kubernetes API request. This prevents the {@code 401 Unauthorized}
 * errors that occur once the initially cached GKE token expires (~60 minutes).
 *
 * <p>{@link GoogleCredentials#refreshIfExpired()} is thread-safe and only calls
 * the token endpoint when the current token is missing or expired.</p>
 */
public class GoogleAuthInterceptor implements Interceptor {

    private static final Logger _log = LoggerFactory.getLogger(GoogleAuthInterceptor.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final GoogleCredentials _credentials;

    public GoogleAuthInterceptor(GoogleCredentials credentials) {
        this._credentials = credentials;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        try {
            _credentials.refreshIfExpired();
        } catch (IOException e) {
            _log.error("Failed to refresh Google credentials for Kubernetes API request", e);
            throw e;
        }

        var token = _credentials.getAccessToken();
        if (token == null || token.getTokenValue() == null) {
            throw new IOException("Unable to obtain a valid Google access token for Kubernetes API request");
        }

        var authorizedRequest = chain.request().newBuilder()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token.getTokenValue())
                .build();

        return chain.proceed(authorizedRequest);
    }
}