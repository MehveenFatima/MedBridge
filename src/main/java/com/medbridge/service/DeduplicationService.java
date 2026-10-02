package com.medbridge.service;

import com.medbridge.entity.Gender;
import com.medbridge.entity.Patient;
import com.medbridge.entity.PatientSourceRecord;
import com.medbridge.entity.SourceSystem;
import com.medbridge.repository.PatientRepository;
import com.medbridge.repository.PatientSourceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Decides whether an incoming (already validated and normalised) record describes a
 * patient we already know, and links it.
 *
 * Rule 1 - same source, same MRN -> same patient.
 *          A source's MRNs are internally consistent; that is what an MRN is for.
 * Rule 2 - different source, same last name + date of birth -> same patient.
 *          MRNs mean nothing across sources: the lab's 10001 and the hospital's 10001
 *          can be different people.
 * Otherwise a new canonical patient is created.
 *
 * Conflict policy when a match disagrees with what we hold:
 *   - existing value present, incoming different -> KEEP existing, log a warning
 *   - existing value missing, incoming present   -> fill the gap
 * Never overwrite silently: a clinician must not see a birth date change because a
 * second feed disagreed.
 *
 * Known weakness of rule 2: two different people with the same surname and birth date
 * would be merged. Production systems add more fields, score matches probabilistically
 * and send uncertain pairs to a human for review.
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

    private final PatientRepository patientRepository;
    private final PatientSourceRecordRepository sourceRecordRepository;

    public DeduplicationService(PatientRepository patientRepository,
                                PatientSourceRecordRepository sourceRecordRepository) {
        this.patientRepository = patientRepository;
        this.sourceRecordRepository = sourceRecordRepository;
    }

    /**
     * One transaction per record: the patient and its source link are saved together or
     * not at all, and a failure here never rolls back other records in the same file.
     * (@Transactional works through a Spring proxy, which is why this lives in its own bean
     * rather than as a private method inside IngestionService.)
     */
    @Transactional
    public DeduplicationResult resolve(Patient incoming, SourceSystem source, String sourceMrn, String rawPayload) {

        // Rule 1: this source has sent this MRN before
        Optional<PatientSourceRecord> existingLink =
                sourceRecordRepository.findBySourceSystemIdAndSourceMrn(source.getId(), sourceMrn);
        if (existingLink.isPresent()) {
            PatientSourceRecord link = existingLink.get();
            Patient patient = link.getPatient();
            int conflicts = reconcile(patient, incoming, source.getCode());
            link.setRawPayload(rawPayload);          // keep the latest version the source sent
            sourceRecordRepository.save(link);
            return new DeduplicationResult(patient, MatchType.SAME_SOURCE_MRN, conflicts);
        }

        // Rule 2: another source registered someone with the same last name and date of birth
        Optional<Patient> crossSourceMatch = patientRepository
                .findFirstByLastNameAndDateOfBirthOrderByIdAsc(incoming.getLastName(), incoming.getDateOfBirth());

        Patient patient;
        MatchType matchType;
        int conflicts = 0;
        if (crossSourceMatch.isPresent()) {
            patient = crossSourceMatch.get();
            matchType = MatchType.CROSS_SOURCE_DEMOGRAPHICS;
            conflicts = reconcile(patient, incoming, source.getCode());
        } else {
            patient = patientRepository.save(incoming);
            matchType = MatchType.NEW_PATIENT;
        }

        PatientSourceRecord link = new PatientSourceRecord();
        link.setPatient(patient);
        link.setSourceSystem(source);
        link.setSourceMrn(sourceMrn);
        link.setRawPayload(rawPayload);
        sourceRecordRepository.save(link);

        return new DeduplicationResult(patient, matchType, conflicts);
    }

    /** Applies the conflict policy field by field. Returns the number of conflicts found. */
    private int reconcile(Patient existing, Patient incoming, String sourceCode) {
        FieldMerge merge = new FieldMerge(existing, incoming, sourceCode);
        merge.field("firstName", Patient::getFirstName, existing::setFirstName);
        merge.field("dateOfBirth", Patient::getDateOfBirth, existing::setDateOfBirth);
        merge.field("phone", Patient::getPhone, existing::setPhone);
        merge.field("city", Patient::getCity, existing::setCity);
        merge.field("state", Patient::getState, existing::setState);
        merge.field("postalCode", Patient::getPostalCode, existing::setPostalCode);
        merge.gender();

        if (merge.filledGaps) {
            patientRepository.save(existing);
        }
        return merge.conflicts;
    }

    /** Small helper so the keep-existing / fill-gap / log-conflict rule is written once. */
    private static final class FieldMerge {
        private final Patient existing;
        private final Patient incoming;
        private final String sourceCode;
        private int conflicts;
        private boolean filledGaps;

        FieldMerge(Patient existing, Patient incoming, String sourceCode) {
            this.existing = existing;
            this.incoming = incoming;
            this.sourceCode = sourceCode;
        }

        <T> void field(String name, Function<Patient, T> getter, Consumer<T> setter) {
            T current = getter.apply(existing);
            T offered = getter.apply(incoming);
            if (offered == null) {
                return;
            }
            if (current == null) {
                setter.accept(offered);
                filledGaps = true;
            } else if (!Objects.equals(current, offered)) {
                conflicts++;
                log.warn("Conflict on patient {} field '{}' from {}: keeping '{}', ignoring '{}'",
                        existing.getId(), name, sourceCode, current, offered);
            }
        }

        void gender() {
            Gender current = existing.getGender();
            Gender offered = incoming.getGender();
            if (offered == null || offered == Gender.UNKNOWN) {
                return;                                  // nothing useful offered
            }
            if (current == null || current == Gender.UNKNOWN) {
                existing.setGender(offered);
                filledGaps = true;
            } else if (current != offered) {
                conflicts++;
                log.warn("Conflict on patient {} field 'gender' from {}: keeping '{}', ignoring '{}'",
                        existing.getId(), sourceCode, current, offered);
            }
        }
    }
}
