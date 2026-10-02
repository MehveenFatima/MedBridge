package com.medbridge.entity;

/**
 * Canonical gender values. UNKNOWN is a real value, not an error:
 * "not recorded" is a legitimate state in clinical data.
 */
public enum Gender {
    MALE, FEMALE, OTHER, UNKNOWN
}
