package com.costco.foundation.integration.framework.gcp.scaling.dto.gke.requests;

/**
 * Request body for rolling a service back to a previous revision.
 *
 * @param targetRevision the revision to roll back to; when {@code null} or
 *                       {@code <= 0}, the immediately previous revision is used
 */
public record RollbackRequest(Long targetRevision) {

}