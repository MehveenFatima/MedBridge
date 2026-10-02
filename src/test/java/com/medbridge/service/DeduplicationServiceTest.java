package com.medbridge.service;

import com.medbridge.entity.Gender;
import com.medbridge.entity.Patient;
import com.medbridge.entity.PatientSourceRecord;
import com.medbridge.entity.SourceFormat;
import com.medbridge.entity.SourceSystem;
import com.medbridge.repository.PatientRepository;
import com.medbridge.repository.PatientSourceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests with Mockito: the repositories are fakes we control, so no database is needed
 * and each rule of the matching strategy can be tested in isolation. This is only possible
 * because DeduplicationService receives its repositories through its constructor.
 */
@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    private static final LocalDate DOB = LocalDate.of(1998, 3, 15);

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientSourceRecordRepository sourceRecordRepository;

    private DeduplicationService service;
    private SourceSystem lab;
    private SourceSystem hospital;

    @BeforeEach
    void setUp() {
        service = new DeduplicationService(patientRepository, sourceRecordRepository);
        lab = source(1L, "LAB", SourceFormat.CSV);
        hospital = source(3L, "HOSPITAL", SourceFormat.HL7);
    }

    @Test
    void unknownPatientIsCreatedAndLinkedToItsSource() {
        Patient incoming = patient(null, "Zoya", "Fatima", "9876543210");
        when(sourceRecordRepository.findBySourceSystemIdAndSourceMrn(1L, "10001")).thenReturn(Optional.empty());
        when(patientRepository.findFirstByLastNameAndDateOfBirthOrderByIdAsc("Fatima", DOB)).thenReturn(Optional.empty());
        when(patientRepository.save(incoming)).thenReturn(incoming);

        DeduplicationResult result = service.resolve(incoming, lab, "10001", "raw csv line");

        assertEquals(MatchType.NEW_PATIENT, result.matchType());
        assertTrue(result.isNewPatient());
        ArgumentCaptor<PatientSourceRecord> link = ArgumentCaptor.forClass(PatientSourceRecord.class);
        verify(sourceRecordRepository).save(link.capture());
        assertSame(incoming, link.getValue().getPatient());
        assertEquals("10001", link.getValue().getSourceMrn());
        assertEquals("raw csv line", link.getValue().getRawPayload());
    }

    @Test
    void sameSourceAndMrnMatchesTheExistingPatientAndKeepsExistingValues() {
        Patient existing = patient(7L, "Zoya", "Fatima", "9876543210");
        PatientSourceRecord existingLink = new PatientSourceRecord();
        existingLink.setPatient(existing);
        existingLink.setSourceSystem(lab);
        existingLink.setSourceMrn("10001");
        existingLink.setRawPayload("old line");
        when(sourceRecordRepository.findBySourceSystemIdAndSourceMrn(1L, "10001")).thenReturn(Optional.of(existingLink));

        Patient incoming = patient(null, "Zoya", "Fatima", "9999999999");   // lab re-sent with a new phone
        DeduplicationResult result = service.resolve(incoming, lab, "10001", "new line");

        assertEquals(MatchType.SAME_SOURCE_MRN, result.matchType());
        assertSame(existing, result.patient());
        assertEquals(1, result.conflictCount());
        assertEquals("9876543210", existing.getPhone());          // conflict: existing value kept
        assertEquals("new line", existingLink.getRawPayload());   // latest raw version stored
        verify(patientRepository, never()).save(any());           // no new patient
    }

    @Test
    void differentSourceWithSameNameAndDobLinksToTheExistingPatient() {
        Patient existing = patient(7L, "Zoya", "Fatima", null);   // lab never sent a phone
        when(sourceRecordRepository.findBySourceSystemIdAndSourceMrn(3L, "H-555")).thenReturn(Optional.empty());
        when(patientRepository.findFirstByLastNameAndDateOfBirthOrderByIdAsc("Fatima", DOB)).thenReturn(Optional.of(existing));

        Patient incoming = patient(null, "Zoya", "Fatima", "9876543210");
        DeduplicationResult result = service.resolve(incoming, hospital, "H-555", "PID|...");

        assertEquals(MatchType.CROSS_SOURCE_DEMOGRAPHICS, result.matchType());
        assertEquals(0, result.conflictCount());
        assertEquals("9876543210", existing.getPhone());   // gap filled from the new source
        verify(patientRepository).save(existing);
        ArgumentCaptor<PatientSourceRecord> link = ArgumentCaptor.forClass(PatientSourceRecord.class);
        verify(sourceRecordRepository).save(link.capture());
        assertSame(existing, link.getValue().getPatient());
        assertSame(hospital, link.getValue().getSourceSystem());
    }

    @Test
    void conflictingFirstNameIsCountedButNotOverwritten() {
        Patient existing = patient(7L, "Zoya", "Fatima", "9876543210");
        when(sourceRecordRepository.findBySourceSystemIdAndSourceMrn(3L, "H-555")).thenReturn(Optional.empty());
        when(patientRepository.findFirstByLastNameAndDateOfBirthOrderByIdAsc("Fatima", DOB)).thenReturn(Optional.of(existing));

        DeduplicationResult result = service.resolve(patient(null, "Mehvin", "Fatima", "9876543210"),
                hospital, "H-555", "PID|...");

        assertEquals(1, result.conflictCount());
        assertEquals("Zoya", existing.getFirstName());
    }

    private static Patient patient(Long id, String first, String last, String phone) {
        Patient p = new Patient();
        p.setId(id);
        p.setMrn("10001");
        p.setFirstName(first);
        p.setLastName(last);
        p.setDateOfBirth(DOB);
        p.setGender(Gender.FEMALE);
        p.setPhone(phone);
        return p;
    }

    private static SourceSystem source(Long id, String code, SourceFormat format) {
        SourceSystem s = new SourceSystem();
        s.setId(id);
        s.setCode(code);
        s.setName(code);
        s.setFormat(format);
        return s;
    }
}
