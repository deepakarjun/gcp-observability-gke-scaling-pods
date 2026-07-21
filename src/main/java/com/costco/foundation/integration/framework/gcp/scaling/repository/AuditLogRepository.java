package com.costco.foundation.integration.framework.gcp.scaling.repository;

import com.costco.foundation.integration.framework.gcp.scaling.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data-access layer for {@link AuditLog} records, with dynamic filtering and
 * retention support.
 */
@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /**
     * Deletes audit records created before the given cutoff (retention purge).
     *
     * @param cutoff the exclusive upper bound for {@code createdAt}
     * @return the number of records deleted
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}