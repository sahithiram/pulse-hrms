package com.mentis.hrms.pm.exception;

/**
 * Thrown when the current PM user does not have permission to perform
 * the requested operation. Mapped to HTTP 403 by PmRuleExceptionHandler.
 */
public class PmAccessDeniedException extends RuntimeException {
    public PmAccessDeniedException(String message) {
        super(message);
    }
}