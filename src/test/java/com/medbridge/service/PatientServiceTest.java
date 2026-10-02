package com.medbridge.service;

import com.medbridge.dto.PatientResponse;
import com.medbridge.entity.Gender;
import com.medbridge.entity.Patient;
import com.medbridge.exception.ResourceNotFoundException;
import com.medbridge.mapper.PatientMapper;
import com.medbridge.repository.PatientRepository;
import com.medbridge.repository.PatientSourceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientSourceRecordRepository sourceRecordRepository;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(patientRepository, sourceRecordRepository, new PatientMapper());
    }

    @Test
    void findByIdReturnsTheMappedPatient() {
        Patient p = new Patient();
        p.setId(1L);
        p.setMrn("10001");
        p.setFirstName("Zoya");
        p.setLastName("Fatima");
        p.setDateOfBirth(LocalDate.of(1998, 3, 15));
        p.setGender(Gender.FEMALE);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(p));

        PatientResponse response = service.findById(1L);

        assertEquals("Fatima", response.lastName());
        assertEquals(Gender.FEMALE, response.gender());
    }

    @Test
    void findByIdThrowsNotFoundForUnknownId() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));

        assertTrue(ex.getMessage().contains("99"));
    }

    @Test
    void sourceRecordsOfUnknownPatientIs404AndDoesNotQueryLinks() {
        when(patientRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.findSourceRecords(99L));
        verify(sourceRecordRepository, never()).findByPatientIdWithSourceSystem(anyLong());
    }

    @Test
    void searchByMrnTrimsInput() {
        when(patientRepository.findByMrn("10001")).thenReturn(List.of());

        assertTrue(service.findByMrn("  10001 ").isEmpty());
    }
}
