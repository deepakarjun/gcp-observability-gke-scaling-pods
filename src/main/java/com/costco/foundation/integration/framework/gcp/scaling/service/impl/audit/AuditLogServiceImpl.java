package com.costco.foundation.integration.framework.gcp.scaling.service.impl.audit;

import com.costco.foundation.integration.framework.gcp.scaling.configs.AuditRetentionProperties;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogFilter;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import com.costco.foundation.integration.framework.gcp.scaling.entity.AuditLog;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.repository.AuditLogRepository;
import com.costco.foundation.integration.framework.gcp.scaling.repository.spec.AuditLogSpecifications;
import com.costco.foundation.integration.framework.gcp.scaling.service.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Default implementation that persists, queries, and prunes audit records.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger _log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository _auditLogRepository;
    private final AuditRetentionProperties _retentionProperties;

    public AuditLogServiceImpl(
            AuditLogRepository auditLogRepository,
            AuditRetentionProperties retentionProperties) {
        _auditLogRepository = auditLogRepository;
        _retentionProperties = retentionProperties;
    }

    @Override
    @Transactional
    public AuditLogResponse record(AuditRecordCommand command) {
        try {
            var saved = _auditLogRepository.save(toEntity(command));
            _log.info("Recorded audit entry '{}' for action '{}' on service '{}' in cluster '{}'",
                    saved.getId(), command.action(), command.serviceName(), command.clusterName());
            return toResponse(saved);
        } catch (Exception ex) {
            _log.error("Failed to record audit entry for action '{}' on service '{}'",
                    command.action(), command.serviceName(), ex);
            throw new ScalingException("Failed to record audit entry", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        _log.info("Searching audit entries with filter: {}", filter);
        return _auditLogRepository
                .findAll(AuditLogSpecifications.fromFilter(filter), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public int purgeExpired() {
        var cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(_retentionProperties.retentionDays());
        var deleted = _auditLogRepository.deleteByCreatedAtBefore(cutoff);
        _log.info("Purged {} audit entries older than {}", deleted, cutoff);
        return deleted;
    }

    private AuditLog toEntity(AuditRecordCommand command) {
        return AuditLog.builder()
                .action(command.action())
                .status(command.status())
                .projectId(command.projectId())
                .clusterName(command.clusterName())
                .namespace(command.namespace())
                .serviceName(command.serviceName())
                .responsePayload(command.responsePayload())
                .errorMessage(command.errorMessage())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private AuditLogResponse toResponse(AuditLog entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getAction(),
                entity.getStatus(),
                entity.getProjectId(),
                entity.getClusterName(),
                entity.getNamespace(),
                entity.getServiceName(),
                entity.getResponsePayload(),
                entity.getErrorMessage(),
                entity.getCreatedAt());
    }
}