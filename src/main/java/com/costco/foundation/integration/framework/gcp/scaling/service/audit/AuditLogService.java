package com.costco.foundation.integration.framework.gcp.scaling.service.audit;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Records and retrieves audit entries for scaling operations.
 */
public interface AuditLogService {

    /**
     * Persists an audit record for a completed operation.
     *
     * @param command the record to store
     * @return the stored entry
     */
    AuditLogResponse record(AuditRecordCommand command);

    /**
     * Retrieves audit entries, optionally filtered by action.
     *
     * @param action   optional action filter; {@code null} returns all
     * @param pageable pagination and sorting
     * @return a page of audit entries
     */
    Page<AuditLogResponse> findAll(AuditAction action, Pageable pageable);
}