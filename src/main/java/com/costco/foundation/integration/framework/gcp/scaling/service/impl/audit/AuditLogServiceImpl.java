package com.costco.foundation.integration.framework.gcp.scaling.service.impl.audit;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogResponse;
import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditRecordCommand;
import com.costco.foundation.integration.framework.gcp.scaling.entity.AuditLog;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import com.costco.foundation.integration.framework.gcp.scaling.exception.ScalingException;
import com.costco.foundation.integration.framework.gcp.scaling.repository.AuditLogRepository;
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
 * Default implementation that persists audit records to the configured
 * datastore and maps entities to response DTOs.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger _log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository _auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        _auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public AuditLogResponse record(AuditRecordCommand command) {
        try {
            var entity = toEntity(command);
            var saved = _auditLogRepository.save(entity);
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
    public Page<AuditLogResponse> findAll(AuditAction action, Pageable pageable) {
        _log.info("Fetching audit entries (action filter: {})", action);
        var page = (action != null)
                ? _auditLogRepository.findByAction(action, pageable)
                : _auditLogRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    private AuditLog toEntity(AuditRecordCommand command) {
        var entity = new AuditLog();
        entity.setAction(command.action());
        entity.setStatus(command.status());
        entity.setProjectId(command.projectId());
        entity.setClusterName(command.clusterName());
        entity.setNamespace(command.namespace());
        entity.setServiceName(command.serviceName());
        entity.setResponsePayload(command.responsePayload());
        entity.setErrorMessage(command.errorMessage());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
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