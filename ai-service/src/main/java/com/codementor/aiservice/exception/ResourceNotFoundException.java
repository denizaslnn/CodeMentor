package com.codementor.aiservice.exception;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String message) {
        super("error.resource.notfound", "RESOURCE_NOT_FOUND", message);
    }
}
