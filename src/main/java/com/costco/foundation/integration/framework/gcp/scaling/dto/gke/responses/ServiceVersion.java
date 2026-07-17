package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.responses;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A single revision in a service's rollout history.
 *
 * @param revision   the deployment revision number (higher is newer)
 * @param images     container images used in this revision
 * @param replicas   desired replicas recorded for this revision's ReplicaSet
 * @param createdAt  when the revision's ReplicaSet was created
 * @param current    {@code true} if this is the currently active revision
 */
public record ServiceVersion(
        long revision,
        List<String> images,
        int replicas,
        OffsetDateTime createdAt,
        boolean current) {
}