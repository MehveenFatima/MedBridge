package com.medbridge.validator;

/** One problem with one field of one record. A Java record: immutable, no boilerplate. */
public record FieldError(String field, String message) {
}
