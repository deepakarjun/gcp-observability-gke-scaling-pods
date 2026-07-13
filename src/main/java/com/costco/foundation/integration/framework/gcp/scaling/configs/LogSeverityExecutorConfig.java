package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides the executor used to parallelize Cloud Logging severity queries.
 */
@Configuration
public class LogSeverityExecutorConfig {

    /** Bean name for the log-severity query executor. */
    public static final String LOG_SEVERITY_EXECUTOR_BEAN = "logSeverityExecutor";

    /** Default pool size when the property is not configured. */
    private static final String DEFAULT_POOL_SIZE = "5";

    /**
     * Fixed thread pool for concurrent severity counting. Sized to comfortably
     * cover (namespaces x severities) fan-out without exhausting resources.
     * Spring shuts it down automatically on context close.
     *
     * @param poolSize the configured pool size
     * @return the executor service
     */
    @Bean(name = LOG_SEVERITY_EXECUTOR_BEAN, destroyMethod = "shutdown")
    public ExecutorService logSeverityExecutor(
            @Value("${gke.log-severity.thread-pool-size:" + DEFAULT_POOL_SIZE + "}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize);
    }
}