package com.medbridge.exception;

/**
 * The uploaded file as a whole could not be read (malformed JSON, missing CSV columns...).
 * Mapped to 422 Unprocessable Entity.
 *
 * Problems with individual records are NOT exceptions: they become IngestionError rows
 * and the rest of the file carries on.
 */
public class ParseException extends RuntimeException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
