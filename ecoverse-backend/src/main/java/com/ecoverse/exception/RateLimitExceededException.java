package com.ecoverse.exception;

/**
 * Thrown when a user exceeds the rate limit for an endpoint.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
