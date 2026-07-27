package com.costco.foundation.integration.framework.gcp.scaling.audit;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;
import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Cross-cutting aspect that records audit entries for methods annotated with
 * {@link Auditable}. Context is taken from an explicit {@link AuditContext}
 * argument on the annotated method, avoiding brittle parameter-name inference.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger _log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogService _auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        _auditLogService = auditLogService;
    }

    /**
     * Wraps an {@link Auditable} method, persisting a SUCCESS record with the
     * result body or a FAILURE record with the error, then re-throwing.
     *
     * @param joinPoint the intercepted method
     * @param auditable the annotation carrying the action
     * @return the original method result
     * @throws Throwable any exception thrown by the target method
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        var context = resolveContext(joinPoint);
        try {
            var result = joinPoint.proceed();
            recordSafely(auditable, context, AuditStatus.SUCCESS, normalizePayload(result), null);
            return result;
        } catch (Throwable ex) {
            recordSafely(auditable, context, AuditStatus.FAILURE, null, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Finds the {@link AuditContext} among the method arguments.
     *
     * @param joinPoint the intercepted method
     * @return the supplied context
     * @throws IllegalStateException when no {@link AuditContext} argument exists
     */
    private AuditContext resolveContext(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
                .filter(AuditContext.class::isInstance)
                .map(AuditContext.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "@Auditable method '" + joinPoint.getSignature()
                                + "' must declare an AuditContext parameter"));
    }

    /**
     * Records the audit entry, isolating any audit failure so it never breaks the
     * primary operation.
     */
    private void recordSafely(
            Auditable auditable, AuditContext context,
            AuditStatus status, Object payload, String error) {
        try {
            _auditLogService.record(new AuditRecordCommand(
                    auditable.value(),
                    status,
                    context.projectId(),
                    context.clusterName(),
                    context.namespace(),
                    context.serviceName(),
                    payload,
                    error));
        } catch (Exception auditEx) {
            _log.error("Failed to persist audit entry for action '{}'; primary operation unaffected",
                    auditable.value(), auditEx);
        }
    }

    /**
     * Normalizes a method result into a JSON-serializable payload. Unwraps a
     * {@link ResponseEntity} to its body so persistence never attempts to
     * (de)serialize framework wrapper types that lack a default constructor.
     *
     * @param result the raw method return value
     * @return the body when a {@link ResponseEntity}, otherwise the result itself
     */
    private Object normalizePayload(Object result) {
        return Optional.ofNullable(result)
                .map(r -> r instanceof ResponseEntity<?> entity ? entity.getBody() : r)
                .orElse(null);
    }
}

// package com.costco.foundation.integration.framework.gcp.scaling.audit;

// import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
// import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;
// import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
// import org.aspectj.lang.ProceedingJoinPoint;
// import org.aspectj.lang.annotation.Around;
// import org.aspectj.lang.annotation.Aspect;
// import org.aspectj.lang.reflect.MethodSignature;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.stereotype.Component;
// import org.springframework.http.ResponseEntity;

// import java.lang.reflect.Method;
// import java.util.HashMap;
// import java.util.Map;

// /**
//  * Cross-cutting aspect that records audit entries for methods annotated with
//  * {@link Auditable}, capturing the return value on success or the error message
//  * on failure. Keeps business services free of audit concerns.
//  */
// @Aspect
// @Component
// public class AuditAspect {

//     private static final Logger _log = LoggerFactory.getLogger(AuditAspect.class);

//     /** Recognised context parameter names (no magic strings scattered around). */
//     private static final String PARAM_PROJECT_ID = "projectId";
//     private static final String PARAM_CLUSTER_ID = "clusterId";
//     private static final String PARAM_CLUSTER_NAME = "clusterName";
//     private static final String PARAM_NAMESPACE = "namespace";
//     private static final String PARAM_SERVICE_NAME = "serviceName";

//     /** Accessor method names probed on request DTOs for context enrichment. */
//     private static final String ACCESSOR_SERVICE_NAME = "serviceName";
//     private static final String ACCESSOR_NAMESPACE = "namespace";
//     private static final String ACCESSOR_CLUSTER_NAME = "clusterName";
//     private static final String ACCESSOR_PROJECT_ID = "projectId";

//     private final AuditLogService _auditLogService;

//     public AuditAspect(AuditLogService auditLogService) {
//         _auditLogService = auditLogService;
//     }

//     /**
//      * Wraps an {@link Auditable} method, persisting a SUCCESS record with the
//      * result or a FAILURE record with the error, then re-throwing.
//      *
//      * @param joinPoint the intercepted method
//      * @param auditable the annotation carrying the action
//      * @return the original method result
//      * @throws Throwable any exception thrown by the target method
//      */
//     @Around("@annotation(auditable)")
//     public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
//         var context = extractContext(joinPoint);
//         try {
//             var result = joinPoint.proceed();
//             recordSafely(auditable, context, AuditStatus.SUCCESS, normalizePayload(result), null);
//             return result;
//         } catch (Throwable ex) {
//             recordSafely(auditable, context, AuditStatus.FAILURE, null, ex.getMessage());
//             throw ex;
//         }
//     }

//     /**
//      * Normalizes a method result into a JSON-serializable payload. Unwraps a
//      * {@link ResponseEntity} to its body so persistence never attempts to
//      * (de)serialize framework wrapper types that lack a default constructor.
//      *
//      * @param result the raw method return value
//      * @return the body when a {@link ResponseEntity}, otherwise the result itself
//      */
//     private Object normalizePayload(Object result) {
//         if (result instanceof ResponseEntity<?> responseEntity) {
//             return responseEntity.getBody();
//         }
//         return result;
//     }

//     /**
//      * Records the audit entry, isolating any audit failure so it never breaks
//      * the primary operation.
//      */
//     private void recordSafely(
//             Auditable auditable, Map<String, String> context,
//             AuditStatus status, Object payload, String error) {
//         try {
//             _auditLogService.record(new AuditRecordCommand(
//                     auditable.value(),
//                     status,
//                     context.get(PARAM_PROJECT_ID),
//                     context.get(PARAM_CLUSTER_NAME),
//                     context.get(PARAM_NAMESPACE),
//                     context.get(PARAM_SERVICE_NAME),
//                     payload,
//                     error));
//         } catch (Exception auditEx) {
//             _log.error("Failed to persist audit entry for action '{}'; primary operation unaffected",
//                     auditable.value(), auditEx);
//         }
//     }

//     /**
//      * Extracts recognised context parameters from the method signature, mapping
//      * both {@code clusterId} and {@code clusterName} to a normalised cluster key.
//      */
//     private Map<String, String> extractContext(ProceedingJoinPoint joinPoint) {
//         var signature = (MethodSignature) joinPoint.getSignature();
//         var names = signature.getParameterNames();
//         var args = joinPoint.getArgs();
//         var context = new HashMap<String, String>();

//         for (var i = 0; i < names.length; i++) {
//             var arg = args[i];
//             if (arg instanceof String value) {
//                 mapStringParam(names[i], value, context);
//             } else if (arg != null) {
//                 enrichFromDto(arg, context);
//             }
//         }
//         return context;
//     }

//     /** Maps a recognised String path/query parameter into the context. */
//     private void mapStringParam(String name, String value, Map<String, String> context) {
//         switch (name) {
//             case PARAM_PROJECT_ID -> context.put(PARAM_PROJECT_ID, value);
//             case PARAM_CLUSTER_ID, PARAM_CLUSTER_NAME -> context.put(PARAM_CLUSTER_NAME, value);
//             case PARAM_NAMESPACE -> context.put(PARAM_NAMESPACE, value);
//             case PARAM_SERVICE_NAME -> context.put(PARAM_SERVICE_NAME, value);
//             default -> { /* ignore unrelated params */ }
//         }
//     }

//     /**
//      * Enriches context from a request DTO by invoking known no-arg accessors,
//      * without coupling the aspect to any specific DTO type. Only fills values not
//      * already resolved from path/query parameters.
//      */
//     private void enrichFromDto(Object dto, Map<String, String> context) {
//         context.computeIfAbsent(PARAM_PROJECT_ID, k -> readString(dto, ACCESSOR_PROJECT_ID));
//         context.computeIfAbsent(PARAM_CLUSTER_NAME, k -> readString(dto, ACCESSOR_CLUSTER_NAME));
//         context.computeIfAbsent(PARAM_NAMESPACE, k -> readString(dto, ACCESSOR_NAMESPACE));
//         context.computeIfAbsent(PARAM_SERVICE_NAME, k -> readString(dto, ACCESSOR_SERVICE_NAME));
//     }

//     /**
//      * Reflectively reads a String value from a no-arg accessor, returning
//      * {@code null} when the accessor is absent or non-String.
//      */
//     private String readString(Object dto, String accessor) {
//         try {
//             Method method = dto.getClass().getMethod(accessor);
//             var value = method.invoke(dto);
//             return (value instanceof String s) ? s : null;
//         } catch (Exception ignored) {
//             return null;
//         }
//     }
// }