package com.costco.foundation.integration.framework.gcp.scaling.repository;

import com.costco.foundation.integration.framework.gcp.scaling.entity.AuditLog;
import com.costco.foundation.integration.framework.gcp.scaling.enums.gke.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data-access layer for {@link AuditLog} records.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<AuditLog> findByClusterNameAndServiceName(
            String clusterName, String serviceName, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(
            OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}