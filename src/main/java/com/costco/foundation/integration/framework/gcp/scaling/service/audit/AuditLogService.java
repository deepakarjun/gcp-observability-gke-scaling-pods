package com.costco.foundation.integration.framework.gcp.scaling.service.audit;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogFilter;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Records, retrieves, and prunes audit entries for scaling operations.
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
     * Retrieves audit entries matching the given filter.
     *
     * @param filter   optional criteria; {@code null} returns all
     * @param pageable pagination and sorting
     * @return a page of audit entries
     */
    Page<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable);

    /**
     * Deletes audit entries older than the configured retention window.
     *
     * @return the number of records purged
     */
    int purgeExpired();
}