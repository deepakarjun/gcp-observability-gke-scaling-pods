package com.costco.foundation.integration.framework.gcp.scaling.exception;

/**
 * Thrown when a requested replica count is outside the allowed HPA bounds.
 */
public class InvalidScaleRequestException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidScaleRequestException(String message) {
        super(message);
    }
}