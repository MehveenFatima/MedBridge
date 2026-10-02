package com.medbridge.service;

import com.medbridge.dto.PageResponse;
import com.medbridge.dto.PatientResponse;
import com.medbridge.dto.SourceRecordResponse;
import com.medbridge.exception.ResourceNotFoundException;
import com.medbridge.mapper.PatientMapper;
import com.medbridge.repository.PatientRepository;
import com.medbridge.repository.PatientSourceRecordRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side for canonical patients. readOnly transactions let Hibernate skip dirty checking. */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientSourceRecordRepository sourceRecordRepository;
    private final PatientMapper mapper;

    public PatientService(PatientRepository patientRepository,
                          PatientSourceRecordRepository sourceRecordRepository,
                          PatientMapper mapper) {
        this.patientRepository = patientRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.mapper = mapper;
    }

    public PageResponse<PatientResponse> findAll(Pageable pageable) {
        return PageResponse.from(patientRepository.findAll(pageable).map(mapper::toResponse));
    }

    public PatientResponse findById(Long id) {
        return patientRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /** MRNs are not globally unique, so a search can legitimately return more than one patient. */
    public List<PatientResponse> findByMrn(String mrn) {
        return patientRepository.findByMrn(mrn.trim()).stream().map(mapper::toResponse).toList();
    }

    /** The original records, one per contributing source, that were reconciled into this patient. */
    public List<SourceRecordResponse> findSourceRecords(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        return sourceRecordRepository.findByPatientIdWithSourceSystem(patientId).stream()
                .map(mapper::toSourceRecordResponse)
                .toList();
    }
}
