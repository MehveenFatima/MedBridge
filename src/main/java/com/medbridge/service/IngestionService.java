package com.medbridge.service;

import com.medbridge.dto.IngestionResponse;
import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.BatchStatus;
import com.medbridge.entity.IngestionBatch;
import com.medbridge.entity.IngestionError;
import com.medbridge.entity.SourceSystem;
import com.medbridge.exception.InvalidRequestException;
import com.medbridge.exception.ParseException;
import com.medbridge.mapper.BatchMapper;
import com.medbridge.normalizer.PatientRecordNormalizer;
import com.medbridge.parser.ParserFactory;
import com.medbridge.repository.IngestionBatchRepository;
import com.medbridge.repository.IngestionErrorRepository;
import com.medbridge.repository.SourceSystemRepository;
import com.medbridge.validator.FieldError;
import com.medbridge.validator.PatientRecordValidator;
import com.medbridge.validator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs one uploaded file through the whole pipeline:
 *
 *     parse -> validate -> normalise -> deduplicate -> persist
 *
 * and records what happened in an IngestionBatch.
 *
 * Transaction boundary: deliberately NOT @Transactional as a whole. Each record is saved
 * in its own transaction (DeduplicationService.resolve), so 3 bad records in a file of 100
 * do not stop the other 97. The 3 are logged as IngestionError rows with line numbers.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final SourceSystemRepository sourceSystemRepository;
    private final IngestionBatchRepository batchRepository;
    private final IngestionErrorRepository errorRepository;
    private final ParserFactory parserFactory;
    private final PatientRecordValidator validator;
    private final PatientRecordNormalizer normalizer;
    private final DeduplicationService deduplicationService;
    private final BatchMapper batchMapper;

    public IngestionService(SourceSystemRepository sourceSystemRepository,
                            IngestionBatchRepository batchRepository,
                            IngestionErrorRepository errorRepository,
                            ParserFactory parserFactory,
                            PatientRecordValidator validator,
                            PatientRecordNormalizer normalizer,
                            DeduplicationService deduplicationService,
                            BatchMapper batchMapper) {
        this.sourceSystemRepository = sourceSystemRepository;
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        this.parserFactory = parserFactory;
        this.validator = validator;
        this.normalizer = normalizer;
        this.deduplicationService = deduplicationService;
        this.batchMapper = batchMapper;
    }

    public IngestionResponse ingest(MultipartFile file, String sourceCode) {
        SourceSystem source = resolveSource(sourceCode);
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Uploaded file is empty");
        }

        IngestionBatch batch = startBatch(source, file.getOriginalFilename());

        // 1. PARSE - a file that cannot be read at all fails the batch (and is still recorded)
        List<PatientRecordDTO> records;
        try (InputStream in = file.getInputStream()) {
            records = parserFactory.getParser(source.getFormat()).parse(in);
        } catch (ParseException e) {
            failBatch(batch, e.getMessage());
            throw e;
        } catch (IOException e) {
            failBatch(batch, "Could not read uploaded file");
            throw new ParseException("Could not read uploaded file", e);
        }

        int successes = 0, failures = 0, newPatients = 0, matched = 0, conflicts = 0;

        for (PatientRecordDTO dto : records) {
            // 2. VALIDATE - collects every problem in the record
            ValidationResult validation = validator.validate(dto);
            if (!validation.isValid()) {
                saveErrors(batch, dto, validation.errors());
                failures++;
                continue;                      // this record fails alone; the rest of the file carries on
            }

            // 3-5. NORMALISE, DEDUPLICATE, PERSIST - one transaction per record
            try {
                DeduplicationResult outcome = deduplicationService.resolve(
                        normalizer.toPatient(dto), source, dto.getMrn().trim(), dto.getRawLine());
                successes++;
                if (outcome.isNewPatient()) {
                    newPatients++;
                } else {
                    matched++;
                }
                conflicts += outcome.conflictCount();
            } catch (RuntimeException e) {
                // e.g. a database constraint we did not anticipate: log it against the line, keep going
                log.error("Batch {} line {}: record could not be saved", batch.getId(), dto.getLineNumber(), e);
                saveErrors(batch, dto, List.of(new FieldError("record",
                        "Record could not be saved: " + NestedExceptionUtils.getMostSpecificCause(e).getMessage())));
                failures++;
            }
        }

        batch.setTotalRecords(records.size());
        batch.setSuccessCount(successes);
        batch.setFailureCount(failures);
        batch.setNewPatientCount(newPatients);
        batch.setMatchedPatientCount(matched);
        batch.setConflictCount(conflicts);
        batch.setStatus(BatchStatus.COMPLETED);
        batch.setCompletedAt(LocalDateTime.now());
        batchRepository.save(batch);

        log.info("Batch {} ({} from {}): {} records, {} saved ({} new, {} matched), {} failed, {} conflicts",
                batch.getId(), batch.getFileName(), source.getCode(), records.size(),
                successes, newPatients, matched, failures, conflicts);

        return batchMapper.toIngestionResponse(batch);
    }

    private SourceSystem resolveSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidRequestException("sourceSystem is required");
        }
        SourceSystem source = sourceSystemRepository.findByCode(sourceCode.trim().toUpperCase())
                .orElseThrow(() -> new InvalidRequestException("Unknown source system '" + sourceCode
                        + "'. Valid values: " + sourceSystemRepository.findAll().stream()
                        .map(SourceSystem::getCode).toList()));
        if (!source.isActive()) {
            throw new InvalidRequestException("Source system " + source.getCode() + " is inactive");
        }
        return source;
    }

    private IngestionBatch startBatch(SourceSystem source, String fileName) {
        IngestionBatch batch = new IngestionBatch();
        batch.setSourceSystem(source);
        batch.setFileName(fileName != null && fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setStartedAt(LocalDateTime.now());
        return batchRepository.save(batch);
    }

    private void failBatch(IngestionBatch batch, String reason) {
        batch.setStatus(BatchStatus.FAILED);
        batch.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        batch.setCompletedAt(LocalDateTime.now());
        batchRepository.save(batch);
        log.warn("Batch {} failed: {}", batch.getId(), reason);
    }

    private void saveErrors(IngestionBatch batch, PatientRecordDTO dto, List<FieldError> fieldErrors) {
        List<IngestionError> rows = fieldErrors.stream().map(fe -> {
            IngestionError error = new IngestionError();
            error.setBatch(batch);
            error.setLineNumber(dto.getLineNumber());
            error.setRawLine(dto.getRawLine());
            error.setFieldName(fe.field());
            error.setErrorMessage(fe.message().length() > 500 ? fe.message().substring(0, 500) : fe.message());
            return error;
        }).toList();
        errorRepository.saveAll(rows);
    }
}
