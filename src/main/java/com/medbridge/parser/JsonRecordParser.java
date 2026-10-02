package com.medbridge.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.SourceFormat;
import com.medbridge.exception.ParseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * CLINIC feed: a JSON array of patient objects from a patient-portal API.
 *
 * Same data as the CSV, different vocabulary:
 *   patientId (not mrn), given_name / family_name, dob as dd/MM/yyyy,
 *   sex as "Male"/"Female", contact as "+91-XXXXXXXXXX", nested address object.
 * Mapping these to the canonical field names is this class's whole job.
 */
@Component
public class JsonRecordParser implements RecordParser {

    private final ObjectMapper objectMapper;

    public JsonRecordParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PatientRecordDTO> parse(InputStream input) {
        JsonNode root;
        try {
            root = objectMapper.readTree(input);
        } catch (JsonProcessingException e) {
            String where = e.getLocation() == null ? "" : " at line " + e.getLocation().getLineNr();
            throw new ParseException("Malformed JSON" + where + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ParseException("Could not read JSON file: " + e.getMessage(), e);
        }
        if (root == null || !root.isArray()) {
            throw new ParseException("JSON feed must be an array of patient objects");
        }

        List<PatientRecordDTO> records = new ArrayList<>();
        int position = 0;
        for (JsonNode node : root) {
            position++;
            PatientRecordDTO dto = new PatientRecordDTO();
            // a non-object element (e.g. a stray string) becomes a record with no fields,
            // which the validator rejects with specific errors instead of failing the file
            if (node.isObject()) {
                dto.setMrn(text(node, "patientId"));
                dto.setFirstName(text(node, "given_name"));
                dto.setLastName(text(node, "family_name"));
                dto.setDateOfBirth(text(node, "dob"));
                dto.setGender(text(node, "sex"));
                dto.setPhone(text(node, "contact"));

                JsonNode address = node.path("address");
                dto.setCity(text(address, "city"));
                dto.setState(text(address, "state"));
                dto.setPostalCode(text(address, "postalCode"));
            }
            dto.setLineNumber(position);
            dto.setRawLine(node.toString());
            records.add(dto);
        }
        return records;
    }

    /** Missing, null and blank all come back as null. Numbers come back as text ("12345"). */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String s = value.asText();
        return s.isBlank() ? null : s;
    }

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.JSON;
    }
}
