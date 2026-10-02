package com.medbridge.entity;

/**
 * PROCESSING - batch row created, records being processed
 * COMPLETED  - file was readable; individual records may still have failed (see failureCount)
 * FAILED     - the file itself could not be parsed, so no records were processed
 */
public enum BatchStatus {
    PROCESSING, COMPLETED, FAILED
}
