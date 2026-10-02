package com.medbridge.service;

import com.medbridge.entity.Patient;

/** Outcome of resolving one incoming record: which patient it became and how it matched. */
public record DeduplicationResult(Patient patient, MatchType matchType, int conflictCount) {

    public boolean isNewPatient() {
        return matchType == MatchType.NEW_PATIENT;
    }
}
