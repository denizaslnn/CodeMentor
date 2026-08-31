package com.codementor.codeservice.exception;

/**
 * Thrown when a username/password pair does not authenticate.
 * Deliberately uses one generic message for unknown user and wrong password
 * to reduce username enumeration risk.
 */
public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        super("error.auth.invalid", "INVALID_CREDENTIALS");
    }
}