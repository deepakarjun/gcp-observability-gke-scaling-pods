package com.costco.foundation.integration.framework.gcp.scaling.audit;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;
import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cross-cutting aspect that records audit entries for methods annotated with
 * {@link Auditable}, capturing the return value on success or the error message
 * on failure. Keeps business services free of audit concerns.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger _log = LoggerFactory.getLogger(AuditAspect.class);

    /** Recognised context parameter names (no magic strings scattered around). */
    private static final String PARAM_PROJECT_ID = "projectId";
    private static final String PARAM_CLUSTER_ID = "clusterId";
    private static final String PARAM_CLUSTER_NAME = "clusterName";
    private static final String PARAM_NAMESPACE = "namespace";
    private static final String PARAM_SERVICE_NAME = "serviceName";

    private final AuditLogService _auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        _auditLogService = auditLogService;
    }

    /**
     * Wraps an {@link Auditable} method, persisting a SUCCESS record with the
     * result or a FAILURE record with the error, then re-throwing.
     *
     * @param joinPoint the intercepted method
     * @param auditable the annotation carrying the action
     * @return the original method result
     * @throws Throwable any exception thrown by the target method
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        var context = extractContext(joinPoint);
        try {
            var result = joinPoint.proceed();
            recordSafely(auditable, context, AuditStatus.SUCCESS, result, null);
            return result;
        } catch (Throwable ex) {
            recordSafely(auditable, context, AuditStatus.FAILURE, null, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Records the audit entry, isolating any audit failure so it never breaks
     * the primary operation.
     */
    private void recordSafely(
            Auditable auditable, Map<String, String> context,
            AuditStatus status, Object payload, String error) {
        try {
            _auditLogService.record(new AuditRecordCommand(
                    auditable.value(),
                    status,
                    context.get(PARAM_PROJECT_ID),
                    context.get(PARAM_CLUSTER_NAME),
                    context.get(PARAM_NAMESPACE),
                    context.get(PARAM_SERVICE_NAME),
                    payload,
                    error));
        } catch (Exception auditEx) {
            _log.error("Failed to persist audit entry for action '{}'; primary operation unaffected",
                    auditable.value(), auditEx);
        }
    }

    /**
     * Extracts recognised context parameters from the method signature, mapping
     * both {@code clusterId} and {@code clusterName} to a normalised cluster key.
     */
    private Map<String, String> extractContext(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var names = signature.getParameterNames();
        var args = joinPoint.getArgs();
        var context = new HashMap<String, String>();

        for (var i = 0; i < names.length; i++) {
            if (!(args[i] instanceof String value)) {
                continue;
            }
            switch (names[i]) {
                case PARAM_PROJECT_ID -> context.put(PARAM_PROJECT_ID, value);
                case PARAM_CLUSTER_ID, PARAM_CLUSTER_NAME -> context.put(PARAM_CLUSTER_NAME, value);
                case PARAM_NAMESPACE -> context.put(PARAM_NAMESPACE, value);
                case PARAM_SERVICE_NAME -> context.put(PARAM_SERVICE_NAME, value);
                default -> { /* ignore unrelated params */ }
            }
        }
        return context;
    }
}