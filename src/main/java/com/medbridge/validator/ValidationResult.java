package com.medbridge.validator;

import java.util.List;

/** Every problem found in one record. Empty list = valid. */
public record ValidationResult(List<FieldError> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
