package com.medbridge.parser;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.exception.ParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvRecordParserTest {

    private static final String HEADER = "mrn,first_name,last_name,date_of_birth,gender,phone,city,state,postal_code\n";

    private final CsvRecordParser parser = new CsvRecordParser();

    private InputStream csv(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsColumnsByHeaderName() {
        List<PatientRecordDTO> result = parser.parse(csv(HEADER
                + "10001,Zoya,Fatima,1998-03-15,F,9876543210,Hyderabad,TS,500001\n"
                + "10002,Aarav,Sharma,1985-07-22,M,9812345678,Mumbai,MH,400001\n"));

        assertEquals(2, result.size());
        PatientRecordDTO first = result.get(0);
        assertEquals("10001", first.getMrn());
        assertEquals("Zoya", first.getFirstName());
        assertEquals("Fatima", first.getLastName());
        assertEquals("1998-03-15", first.getDateOfBirth());   // still raw: normalising is not the parser's job
        assertEquals("F", first.getGender());
        assertEquals("Hyderabad", first.getCity());
        assertEquals("Sharma", result.get(1).getLastName());
    }

    @Test
    void reportsFileLineNumbersCountingTheHeader() {
        List<PatientRecordDTO> result = parser.parse(csv(HEADER
                + "10001,Zoya,Fatima,1998-03-15,F,9876543210,Hyderabad,TS,500001\n"
                + "10002,Aarav,Sharma,1985-07-22,M,9812345678,Mumbai,MH,400001\n"));

        assertEquals(2, result.get(0).getLineNumber());
        assertEquals(3, result.get(1).getLineNumber());
    }

    /** The reason for a real CSV library instead of line.split(","). */
    @Test
    void handlesQuotedValuesContainingCommas() {
        List<PatientRecordDTO> result = parser.parse(csv(HEADER
                + "10001,Zoya,Fatima,1998-03-15,F,9876543210,\"Hyderabad, Old City\",TS,500001\n"));

        assertEquals("Hyderabad, Old City", result.get(0).getCity());
        assertEquals("TS", result.get(0).getState());
    }

    @Test
    void blankAndMissingValuesBecomeNullForTheValidator() {
        List<PatientRecordDTO> result = parser.parse(csv(HEADER
                + ",Zoya,,1998-03-15,F,9876543210,Hyderabad,TS,500001\n"
                + "10003,Imran,Khan\n"));                              // short row

        assertNull(result.get(0).getMrn());
        assertNull(result.get(0).getLastName());
        assertEquals("Khan", result.get(1).getLastName());
        assertNull(result.get(1).getDateOfBirth());
    }

    @Test
    void failsTheFileWhenRequiredColumnsAreMissing() {
        ParseException ex = assertThrows(ParseException.class,
                () -> parser.parse(csv("mrn,first_name\n10001,Zoya\n")));

        assertTrue(ex.getMessage().contains("last_name"));
    }
}
