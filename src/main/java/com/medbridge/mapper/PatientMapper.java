package com.medbridge.mapper;

import com.medbridge.dto.PatientResponse;
import com.medbridge.dto.SourceRecordResponse;
import com.medbridge.entity.Patient;
import com.medbridge.entity.PatientSourceRecord;
import org.springframework.stereotype.Component;

/** Entity -> response DTO. Called inside service transactions (open-in-view is off). */
@Component
public class PatientMapper {

    public PatientResponse toResponse(Patient p) {
        return new PatientResponse(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(),
                p.getDateOfBirth(), p.getGender(), p.getPhone(), p.getCity(), p.getState(), p.getPostalCode());
    }

    public SourceRecordResponse toSourceRecordResponse(PatientSourceRecord r) {
        return new SourceRecordResponse(r.getId(), r.getSourceSystem().getCode(), r.getSourceSystem().getFormat(),
                r.getSourceMrn(), r.getRawPayload(), r.getReceivedAt(), r.getLastReceivedAt());
    }
}
