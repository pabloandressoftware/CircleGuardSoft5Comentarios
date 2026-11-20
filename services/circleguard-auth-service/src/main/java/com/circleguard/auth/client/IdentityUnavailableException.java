package com.circleguard.auth.client;

/**
 * HU-22 · identity-service no respondio a tiempo o su circuito esta abierto.
 * El login la traduce a un 503 controlado en lugar de un 500.
 */
public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
