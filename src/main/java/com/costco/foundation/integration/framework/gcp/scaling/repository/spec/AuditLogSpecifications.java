package com.costco.foundation.integration.framework.gcp.scaling.repository.spec;

import com.costco.foundation.integration.framework.gcp.scaling.dto.gke.audit.AuditLogFilter;
import com.costco.foundation.integration.framework.gcp.scaling.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

/**
 * Builds {@link Specification} instances for dynamic {@link AuditLog} queries
 * from an {@link AuditLogFilter}. Null filter fields are skipped.
 */
public final class AuditLogSpecifications {

    /** Column name constants (no magic strings). */
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PROJECT_ID = "projectId";
    private static final String FIELD_CLUSTER_NAME = "clusterName";
    private static final String FIELD_NAMESPACE = "namespace";
    private static final String FIELD_SERVICE_NAME = "serviceName";
    private static final String FIELD_CREATED_AT = "createdAt";

    private AuditLogSpecifications() {
    }

    /**
     * @param filter the criteria; {@code null} fields are ignored
     * @return a composed specification matching all provided criteria
     */
    public static Specification<AuditLog> fromFilter(AuditLogFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (filter == null) {
                return cb.conjunction();
            }
            if (filter.action() != null) {
                predicates.add(cb.equal(root.get(FIELD_ACTION), filter.action()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get(FIELD_STATUS), filter.status()));
            }
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get(FIELD_PROJECT_ID), filter.projectId()));
            }
            if (filter.clusterName() != null) {
                predicates.add(cb.equal(root.get(FIELD_CLUSTER_NAME), filter.clusterName()));
            }
            if (filter.namespace() != null) {
                predicates.add(cb.equal(root.get(FIELD_NAMESPACE), filter.namespace()));
            }
            if (filter.serviceName() != null) {
                predicates.add(cb.equal(root.get(FIELD_SERVICE_NAME), filter.serviceName()));
            }
            if (filter.startTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get(FIELD_CREATED_AT), filter.startTime()));
            }
            if (filter.endTime() != null) {
                predicates.add(cb.lessThan(root.get(FIELD_CREATED_AT), filter.endTime()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}