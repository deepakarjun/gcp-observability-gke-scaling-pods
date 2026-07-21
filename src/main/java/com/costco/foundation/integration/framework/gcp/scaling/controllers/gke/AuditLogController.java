package com.costco.foundation.integration.framework.gcp.scaling.controllers.gke;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * Returns a paginated list of audit entries, optionally filtered by action.
     *
     * @param action   optional action filter (SCALE, ROLLBACK, SUSPEND, RESUME)
     * @param pageable pagination/sorting (default 20 per page, newest first)
     * @return a page of audit entries
     */
    @GetMapping
    @Operation(summary = "List audit logs",
            description = "Returns persisted audit entries; survives service redeploys.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "Optional action filter")
            @RequestParam(required = false) AuditAction action,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        _log.info("GET audit-logs (action filter: {})", action);
        return ResponseEntity.ok(_auditLogService.findAll(action, pageable));
    }
}