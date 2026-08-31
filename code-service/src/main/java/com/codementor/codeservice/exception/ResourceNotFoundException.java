package com.codementor.codeservice.exception;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String message) {
        super("error.generic.unexpected", "RESOURCE_NOT_FOUND", message);
    }
}
