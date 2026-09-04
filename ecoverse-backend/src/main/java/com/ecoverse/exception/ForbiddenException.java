package com.ecoverse.exception;

/**
 * Thrown when a user tries to access a resource they don't own.
 * Returns HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String resource, Long resourceId, Long userId) {
        super(String.format("You don't have access to %s with id %d", resource, resourceId));
    }
}
