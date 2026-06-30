package com.costco.foundation.integration.framework.gcp.scaling.exception;

//public class ScalingException {
//
//}
//
//
//package com.costco.scaling.exception;

/**
 * Thrown when a scaling operation fails.
 */
public class ScalingException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ScalingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScalingException(String message) {
        super(message);
    }
}