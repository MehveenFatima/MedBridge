package com.medbridge.parser;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.SourceFormat;
import com.medbridge.exception.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LAB feed: CSV with a header row.
 * mrn, first_name, last_name, date_of_birth (yyyy-MM-dd), gender (M/F), phone, city, state, postal_code
 *
 * Apache Commons CSV rather than line.split(","): a quoted value like "Hyderabad, TS"
 * contains a comma and would break a naive split.
 */
@Component
public class CsvRecordParser implements RecordParser {

    static final List<String> REQUIRED_COLUMNS =
            List.of("mrn", "first_name", "last_name", "date_of_birth", "gender", "phone");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()                 // first row is the header
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    @Override
    public List<PatientRecordDTO> parse(InputStream input) {
        List<PatientRecordDTO> records = new ArrayList<>();

        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             CSVParser csv = FORMAT.parse(reader)) {

            checkRequiredColumns(csv.getHeaderMap().keySet());

            for (CSVRecord row : csv) {
                PatientRecordDTO dto = new PatientRecordDTO();
                dto.setMrn(value(row, "mrn"));
                dto.setFirstName(value(row, "first_name"));
                dto.setLastName(value(row, "last_name"));
                dto.setDateOfBirth(value(row, "date_of_birth"));
                dto.setGender(value(row, "gender"));
                dto.setPhone(value(row, "phone"));
                dto.setCity(value(row, "city"));
                dto.setState(value(row, "state"));
                dto.setPostalCode(value(row, "postal_code"));
                // record 1 is the first data row, which is line 2 of the file (line 1 is the header)
                dto.setLineNumber((int) row.getRecordNumber() + 1);
                dto.setRawLine(String.join(",", row.toList()));
                records.add(dto);
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw new ParseException("Could not read CSV file: " + e.getMessage(), e);
        }
        return records;
    }

    private void checkRequiredColumns(Set<String> headers) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !headers.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new ParseException("CSV file is missing required columns: " + missing);
        }
    }

    /** A short row (fewer values than headers) yields nulls, which the validator then reports. */
    private String value(CSVRecord row, String column) {
        return row.isSet(column) ? blankToNull(row.get(column)) : null;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.CSV;
    }
}
