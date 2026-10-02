package com.medbridge.service;

/** How an incoming record was resolved to a canonical patient. */
public enum MatchType {
    /** No existing patient matched: a new canonical patient was created. */
    NEW_PATIENT,
    /** Rule 1: this source already sent this MRN. */
    SAME_SOURCE_MRN,
    /** Rule 2: a different source already registered someone with the same last name and date of birth. */
    CROSS_SOURCE_DEMOGRAPHICS
}
