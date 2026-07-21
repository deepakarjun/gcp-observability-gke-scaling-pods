package com.costco.foundation.integration.framework.gcp.scaling.audit;

import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose result (or exception) should be recorded in the
 * audit log. Method parameters named {@code projectId}, {@code clusterId}/
 * {@code clusterName}, {@code namespace}, and {@code serviceName} are captured
 * automatically for context.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** @return the operation being audited */
    AuditAction value();
}