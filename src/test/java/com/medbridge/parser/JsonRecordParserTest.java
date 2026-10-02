package com.medbridge.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class JsonRecordParserTest {

    private final JsonRecordParser parser = new JsonRecordParser(new ObjectMapper());

    private InputStream json(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsTheClinicVocabularyToCanonicalFields() {
        List<PatientRecordDTO> result = parser.parse(json("""
                [ { "patientId": "10001", "given_name": "Zoya", "family_name": "Fatima",
                    "dob": "15/03/1998", "sex": "Female", "contact": "+91-9876543210",
                    "address": { "city": "Hyderabad", "state": "TS", "postalCode": "500001" } } ]
                """));

        PatientRecordDTO dto = result.get(0);
        assertEquals("10001", dto.getMrn());
        assertEquals("Zoya", dto.getFirstName());
        assertEquals("Fatima", dto.getLastName());
        assertEquals("15/03/1998", dto.getDateOfBirth());
        assertEquals("Female", dto.getGender());
        assertEquals("+91-9876543210", dto.getPhone());
        assertEquals("Hyderabad", dto.getCity());
        assertEquals("500001", dto.getPostalCode());
        assertEquals(1, dto.getLineNumber());
        assertTrue(dto.getRawLine().contains("\"given_name\":\"Zoya\""));
    }

    @Test
    void acceptsNumericIdsAndMissingAddress() {
        List<PatientRecordDTO> result = parser.parse(json("""
                [ { "patientId": 10001, "family_name": "Fatima", "dob": "15/03/1998" } ]
                """));

        assertEquals("10001", result.get(0).getMrn());
        assertNull(result.get(0).getCity());
        assertNull(result.get(0).getFirstName());
    }

    @Test
    void numbersRecordsByPositionInTheArray() {
        List<PatientRecordDTO> result = parser.parse(json("""
                [ { "patientId": "1", "family_name": "A" }, "not an object", { "patientId": "3", "family_name": "C" } ]
                """));

        assertEquals(3, result.size());
        assertEquals(2, result.get(1).getLineNumber());
        assertNull(result.get(1).getMrn());          // stray element becomes an empty record the validator rejects
        assertEquals(3, result.get(2).getLineNumber());
    }

    @Test
    void failsTheFileWhenJsonIsMalformed() {
        assertThrows(ParseException.class, () -> parser.parse(json("[ { \"patientId\": \"1\", ")));
    }

    @Test
    void failsTheFileWhenRootIsNotAnArray() {
        assertThrows(ParseException.class, () -> parser.parse(json("{ \"patientId\": \"1\" }")));
    }
}
