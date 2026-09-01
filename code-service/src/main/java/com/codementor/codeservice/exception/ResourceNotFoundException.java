package com.codementor.codeservice.exception;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resourceId) {
        super("error.resource.notfound", "RESOURCE_NOT_FOUND", resourceId);
    }
}
