package com.costco.foundation.integration.framework.gcp.scaling.scheduler;

import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically purges audit entries older than the configured retention window.
 */
@Component
public class AuditRetentionScheduler {

    private static final Logger _log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final AuditLogService _auditLogService;

    public AuditRetentionScheduler(AuditLogService auditLogService) {
        _auditLogService = auditLogService;
    }

    /**
     * Runs on the configured cron schedule and delegates to the service to
     * delete expired audit records.
     */
    @Scheduled(cron = "${audit.retention.purge-cron}")
    public void purgeExpiredAuditLogs() {
        try {
            var purged = _auditLogService.purgeExpired();
            _log.info("Audit retention job completed; purged {} entries", purged);
        } catch (Exception ex) {
            _log.error("Audit retention job failed", ex);
        }
    }
}