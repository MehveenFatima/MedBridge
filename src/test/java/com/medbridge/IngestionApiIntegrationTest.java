package com.medbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbridge.entity.PatientSourceRecord;
import com.medbridge.repository.IngestionBatchRepository;
import com.medbridge.repository.IngestionErrorRepository;
import com.medbridge.repository.PatientRepository;
import com.medbridge.repository.PatientSourceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack: real controllers, services, JPA and an in-memory H2 database (profile "h2"),
 * driven through HTTP with MockMvc. Uses the same three feeds as sample-data/.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class IngestionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientSourceRecordRepository sourceRecordRepository;

    @Autowired
    private IngestionBatchRepository batchRepository;

    @Autowired
    private IngestionErrorRepository errorRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        // children before parents, because of the foreign keys
        errorRepository.deleteAllInBatch();
        sourceRecordRepository.deleteAllInBatch();
        batchRepository.deleteAllInBatch();
        patientRepository.deleteAllInBatch();
    }

    private ResultActions upload(String fileName, String sourceSystem) throws Exception {
        byte[] content = new ClassPathResource("feeds/" + fileName).getContentAsByteArray();
        return upload(fileName, content, sourceSystem);
    }

    private ResultActions upload(String fileName, byte[] content, String sourceSystem) throws Exception {
        return mockMvc.perform(multipart("/api/v1/ingest")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                .param("sourceSystem", sourceSystem));
    }

    @Test
    void ingestsAllThreeFeedsAndReconcilesPatientsAcrossSources() throws Exception {
        // parsing and validation outcomes are fixed by the (seeded) sample data
        upload("lab_export.csv", "LAB")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRecords").value(93))
                .andExpect(jsonPath("$.failureCount").value(4))
                // 7 LAB rows re-send an MRN the lab already sent (5 with a new phone, 2 with a new
                // date of birth): matched by rule 1, existing values kept, conflict counted
                .andExpect(jsonPath("$.conflictCount").value(7));
        upload("clinic_feed.json", "CLINIC")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecords").value(94))
                .andExpect(jsonPath("$.failureCount").value(19));
        upload("hospital_adt.hl7", "HOSPITAL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecords").value(83))
                .andExpect(jsonPath("$.failureCount").value(17));

        // 270 records - 40 rejected = 230 accepted. The 7 LAB re-sends update an existing link
        // instead of adding one, so 223 links. Fewer patients than links = cross-source matching happened.
        long links = sourceRecordRepository.count();
        long patients = patientRepository.count();
        assertEquals(270 - 40 - 7, links);
        assertTrue(patients < links, "some records should have matched existing patients");

        // at least one person is now described by all three systems
        // read inside a transaction: the source system is a LAZY association
        Map<Long, Set<String>> sourcesPerPatient = transactionTemplate.execute(status ->
                sourceRecordRepository.findAll().stream()
                        .collect(Collectors.groupingBy(r -> r.getPatient().getId(),
                                Collectors.mapping(this::sourceCode, Collectors.toSet()))));
        Long threeSourcePatient = sourcesPerPatient.entrySet().stream()
                .filter(e -> e.getValue().size() == 3).map(Map.Entry::getKey).findFirst().orElseThrow();

        mockMvc.perform(get("/api/v1/patients/{id}/sources", threeSourcePatient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].sourceSystem").value(org.hamcrest.Matchers.containsInAnyOrder("LAB", "CLINIC", "HOSPITAL")));
    }

    @Test
    void failedRecordsAreReportedWithLineNumbersAndReasons() throws Exception {
        String body = upload("hospital_adt.hl7", "HOSPITAL").andReturn().getResponse().getContentAsString();
        long batchId = objectMapper.readTree(body).get("batchId").asLong();

        mockMvc.perform(get("/api/v1/batches/{id}/errors", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(17))
                .andExpect(jsonPath("$.content[0].lineNumber").isNumber())
                .andExpect(jsonPath("$.content[0].fieldName").isString())
                .andExpect(jsonPath("$.content[0].errorMessage").isString())
                .andExpect(jsonPath("$.content[0].rawLine").value(org.hamcrest.Matchers.startsWith("PID|")));
    }

    @Test
    void reUploadingTheSameFileCreatesNoDuplicates() throws Exception {
        upload("lab_export.csv", "LAB").andExpect(status().isCreated());
        long patientsAfterFirst = patientRepository.count();

        upload("lab_export.csv", "LAB")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newPatientCount").value(0));

        assertEquals(patientsAfterFirst, patientRepository.count());
    }

    @Test
    void hl7NamesAndDatesAreStoredInCanonicalForm() throws Exception {
        byte[] hl7 = ("MSH|^~\\&|CITYHOSP|CENTRAL|MEDBRIDGE|CLINIC|20260920120000||ADT^A01|MSG1|P|2.5\n"
                + "PID|1||55555^^^HOSP^MR||FATIMA^ZOYA^||19980315|F|||Hyderabad^TS^500001||9876543210\n")
                .getBytes(StandardCharsets.UTF_8);
        upload("one.hl7", hl7, "HOSPITAL").andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/patients/search").param("mrn", "55555"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode patient = objectMapper.readTree(body).get(0);
        assertEquals("Zoya", patient.get("firstName").asText());
        assertEquals("Fatima", patient.get("lastName").asText());
        assertEquals("1998-03-15", patient.get("dateOfBirth").asText());
        assertEquals("FEMALE", patient.get("gender").asText());
    }

    @Test
    void malformedFileIs422AndTheFailedBatchIsStillRecorded() throws Exception {
        upload("broken.json", "[ { \"patientId\": \"1\", ".getBytes(StandardCharsets.UTF_8), "CLINIC")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Malformed JSON")));

        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].failureReason").isString());
    }

    @Test
    void unknownSourceSystemIs400() throws Exception {
        upload("lab_export.csv", "PHARMACY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PHARMACY")));
    }

    @Test
    void emptyFileIs400() throws Exception {
        upload("empty.csv", new byte[0], "LAB").andExpect(status().isBadRequest());
    }

    @Test
    void unknownPatientIs404WithTheStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients/999999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void nonNumericIdIs400NotA500() throws Exception {
        mockMvc.perform(get("/api/v1/patients/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownUrlIs404NotA500() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here")).andExpect(status().isNotFound());
    }

    @Test
    void sourceSystemsAreSeeded() throws Exception {
        mockMvc.perform(get("/api/v1/source-systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("LAB"))
                .andExpect(jsonPath("$[0].format").value("CSV"));
    }

    private String sourceCode(PatientSourceRecord record) {
        return record.getSourceSystem().getCode();
    }
}
