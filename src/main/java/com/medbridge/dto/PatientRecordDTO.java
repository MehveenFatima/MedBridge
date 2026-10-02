package com.medbridge.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One record exactly as a parser read it, before normalisation or validation.
 *
 * Every field is a String on purpose. At parse time we have not decided whether
 * "BAD_DATE" is acceptable; that is the validator's job. A parser that threw on bad
 * values could not report WHICH records were bad, or all the problems in one record.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PatientRecordDTO {

    private String mrn;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String gender;
    private String phone;
    private String city;
    private String state;
    private String postalCode;

    /** Line in the file (CSV, HL7) or position in the array (JSON), for error reports. */
    private int lineNumber;

    /** The original text of this record, kept for the audit trail and error log. */
    private String rawLine;
}
