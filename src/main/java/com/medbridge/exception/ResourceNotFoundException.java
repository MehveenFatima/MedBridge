package com.medbridge.exception;

/** A patient, batch or other resource does not exist. Mapped to 404 Not Found. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id " + id);
    }
}
