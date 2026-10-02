package com.medbridge.parser;

import com.medbridge.dto.PatientRecordDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Hl7RecordParserTest {

    private final Hl7RecordParser parser = new Hl7RecordParser();

    private InputStream hl7(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsPositionalFieldsFromThePidSegment() {
        List<PatientRecordDTO> result = parser.parse(hl7("""
                MSH|^~\\&|CITYHOSP|CENTRAL|MEDBRIDGE|CLINIC|20260920120000||ADT^A01|MSG00001|P|2.5
                PID|1||10001^^^HOSP^MR||FATIMA^ZOYA^||19980315|F|||Hyderabad^TS^500001||9876543210
                """));

        assertEquals(1, result.size());
        PatientRecordDTO dto = result.get(0);
        assertEquals("10001", dto.getMrn());                 // PID-3, first component
        assertEquals("FATIMA", dto.getLastName());           // PID-5: family name comes FIRST
        assertEquals("ZOYA", dto.getFirstName());
        assertEquals("19980315", dto.getDateOfBirth());
        assertEquals("F", dto.getGender());
        assertEquals("Hyderabad", dto.getCity());
        assertEquals("TS", dto.getState());
        assertEquals("500001", dto.getPostalCode());
        assertEquals("9876543210", dto.getPhone());
        assertEquals(2, dto.getLineNumber());
    }

    /** A cut-off segment must become validation errors later, not crash the whole file. */
    @Test
    void truncatedSegmentDoesNotThrow() {
        List<PatientRecordDTO> result = parser.parse(hl7("PID|1||10003^^^HOSP^MR||KHAN^IMRAN^\n"));

        assertEquals(1, result.size());
        assertEquals("KHAN", result.get(0).getLastName());
        assertNull(result.get(0).getDateOfBirth());
        assertNull(result.get(0).getPhone());
    }

    @Test
    void ignoresSegmentsOtherThanPid() {
        List<PatientRecordDTO> result = parser.parse(hl7("""
                MSH|^~\\&|CITYHOSP|CENTRAL|MEDBRIDGE|CLINIC|20260920120000||ADT^A01|MSG00001|P|2.5
                EVN|A01|20260920120000
                PID|1||10001^^^HOSP^MR||FATIMA^ZOYA^||19980315|F
                PV1|1|O
                ZXT|vendor-specific segment
                """));

        assertEquals(1, result.size());
    }

    /** Real HL7 separates segments with a carriage return, not a newline. */
    @Test
    void acceptsCarriageReturnSegmentSeparators() {
        List<PatientRecordDTO> result = parser.parse(hl7(
                "MSH|^~\\&|A|B|C|D|20260920120000||ADT^A01|1|P|2.5\r"
                        + "PID|1||10001^^^HOSP^MR||FATIMA^ZOYA^||19980315|F\r"
                        + "MSH|^~\\&|A|B|C|D|20260920120000||ADT^A01|2|P|2.5\r"
                        + "PID|1||10002^^^HOSP^MR||SHARMA^AARAV^||19850722|M\r"));

        assertEquals(2, result.size());
        assertEquals("SHARMA", result.get(1).getLastName());
    }

    @Test
    void emptyTrailingFieldsDoNotShiftPositions() {
        // phone (PID-13) is empty and is the last field: split without -1 would drop it silently
        List<PatientRecordDTO> result = parser.parse(hl7(
                "PID|1||10001^^^HOSP^MR||FATIMA^ZOYA^||19980315|F|||Hyderabad^TS^500001||\n"));

        assertEquals("F", result.get(0).getGender());
        assertEquals("Hyderabad", result.get(0).getCity());
        assertNull(result.get(0).getPhone());
    }
}
