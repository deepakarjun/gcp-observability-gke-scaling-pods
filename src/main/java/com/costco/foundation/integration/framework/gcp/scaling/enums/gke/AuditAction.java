package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

/**
 * The auditable operations tracked by the audit log.
 */
public enum AuditAction {
    SCALE,
    ROLLBACK,
    SUSPEND,
    RESUME
}