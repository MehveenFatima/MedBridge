package com.medbridge.parser;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.SourceFormat;

import java.io.InputStream;
import java.util.List;

/**
 * The contract every feed format implements. The rest of the pipeline only ever sees
 * this interface, never a concrete parser.
 *
 * Adding a fourth format (say FHIR) = one new @Component implementing this interface.
 * ParserFactory picks it up automatically; no existing class changes (open/closed principle).
 */
public interface RecordParser {

    /**
     * Reads every record in the stream. Bad VALUES are returned as-is for the validator
     * to report; only an unreadable FILE throws.
     *
     * @throws com.medbridge.exception.ParseException if the file as a whole cannot be read
     */
    List<PatientRecordDTO> parse(InputStream input);

    SourceFormat getSupportedFormat();
}
