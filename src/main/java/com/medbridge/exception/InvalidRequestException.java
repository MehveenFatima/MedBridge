package com.medbridge.exception;

/** The request itself is wrong (unknown source system, empty file). Mapped to 400 Bad Request. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
