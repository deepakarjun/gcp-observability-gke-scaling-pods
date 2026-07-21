package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogFilter;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditStatus;
import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST endpoints for querying persisted audit logs of scaling operations.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs",
        description = "Query persisted audit history for scale, rollback, suspend, resume actions")
public class AuditLogController {

    private static final Logger _log = LoggerFactory.getLogger(AuditLogController.class);

    private final AuditLogService _auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        _auditLogService = auditLogService;
    }

    /**
     * Returns a paginated, filterable list of audit entries. All filters are
     * optional and combine with AND semantics.
     *
     * @param action      optional action filter
     * @param status      optional status filter
     * @param projectId   optional project filter
     * @param clusterName optional cluster filter
     * @param namespace   optional namespace filter
     * @param serviceName optional service filter
     * @param startTime   optional inclusive lower bound (ISO-8601, UTC)
     * @param endTime     optional exclusive upper bound (ISO-8601, UTC)
     * @param pageable    pagination/sorting (default 20 per page, newest first)
     * @return a page of audit entries
     */
    @GetMapping
    @Operation(summary = "Search audit logs",
            description = "Returns persisted audit entries filtered by any combination of "
                    + "action, status, project, cluster, namespace, service, and time range. "
                    + "Survives service redeploys.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "Filter by action") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Filter by status") @RequestParam(required = false) AuditStatus status,
            @Parameter(description = "Filter by project") @RequestParam(required = false) String projectId,
            @Parameter(description = "Filter by cluster") @RequestParam(required = false) String clusterName,
            @Parameter(description = "Filter by namespace") @RequestParam(required = false) String namespace,
            @Parameter(description = "Filter by service") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Inclusive start (ISO-8601, UTC)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "Exclusive end (ISO-8601, UTC)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        var filter = new AuditLogFilter(
                action, status, projectId, clusterName, namespace, serviceName, startTime, endTime);
        _log.info("GET audit-logs with filter: {}", filter);
        return ResponseEntity.ok(_auditLogService.search(filter, pageable));
    }
}