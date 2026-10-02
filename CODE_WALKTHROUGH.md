# Code Walkthrough

Read this with the code open. For each class: what it does, why it exists as its own class, and the design question it answers.

---

## 1. Follow one request end to end

`POST /api/v1/ingest` with `hospital_adt.hl7` and `sourceSystem=HOSPITAL`:

1. **`IngestionController.ingest()`** receives the multipart upload. It only handles HTTP concerns and calls `IngestionService`.
2. **`IngestionService.ingest()`**
   1. `resolveSource("HOSPITAL")` → the `SourceSystem` row, whose `format` is `HL7`. Unknown code → `InvalidRequestException` → **400**.
   2. Empty file → **400**.
   3. `startBatch()` saves an `IngestionBatch` with status `PROCESSING`.
   4. `parserFactory.getParser(HL7)` → `Hl7RecordParser`, which returns a `List<PatientRecordDTO>` of raw Strings. An unreadable file → `ParseException` → batch marked `FAILED` → **422**.
   5. For each record:
      - `validator.validate(dto)` → if invalid, `saveErrors()` writes one `IngestionError` per problem and the loop **continues** with the next record.
      - if valid, `normalizer.toPatient(dto)` builds a clean `Patient`, then `deduplicationService.resolve(...)` saves it or links it to an existing patient, **in its own transaction**.
   6. The batch counts are filled in, status `COMPLETED`, saved.
3. The controller returns **201 Created** with a `Location: /api/v1/batches/{id}` header and the summary.

---

## 2. Class by class

### Entry point and config

| Class | What it does | Likely question |
|---|---|---|
| `MedbridgeApplication` | Starts Spring Boot. | *What does `@SpringBootApplication` do?* It combines `@Configuration`, `@EnableAutoConfiguration` and `@ComponentScan`. Scanning starts at `com.medbridge`, so every annotated class under it becomes a bean. |
| `config/OpenApiConfig` | A `@Bean` that sets the title and description shown in Swagger UI. | *What is a `@Bean` method?* A method whose return value Spring manages as a bean. |

### Entities (`entity/`): the database tables

| Class | What it does | Likely question |
|---|---|---|
| `Patient` | One canonical person. Index on `(last_name, date_of_birth)` for the dedup lookup. | *Why `EnumType.STRING`?* ORDINAL stores 0/1/2; reordering the enum later would silently corrupt every row. |
| `SourceSystem` | LAB / CLINIC / HOSPITAL and their format. Seeded by `data.sql`. | *Why store the format in the database?* Adding a source that sends an existing format is a data change, not a code change. |
| `PatientSourceRecord` | Links a patient to one source: that source's MRN and the raw payload. Unique on `(source_system_id, source_mrn)`. | *Why `FetchType.LAZY`?* `@ManyToOne` defaults to EAGER, which loads the related row on every fetch whether needed or not. |
| `IngestionBatch` | One upload: status, timings, counts. | *Why record a failed upload at all?* So "what happened to the file sent at 10:42?" has an answer. |
| `IngestionError` | One rejected field of one record, with line number and raw text. | *Why keep the raw line?* Support needs to see exactly what arrived, not our interpretation of it. |
| `Gender`, `SourceFormat`, `BatchStatus` | Enums. `UNKNOWN` is a real gender value, not an error. | |

`@CreationTimestamp` / `@UpdateTimestamp` are Hibernate annotations that fill in timestamps automatically. `@Getter @Setter @NoArgsConstructor` are Lombok: they generate the boilerplate at compile time.

### Repositories (`repository/`): data access with no implementation class

| Interface | Notable methods |
|---|---|
| `PatientRepository` | `findByMrn`, `findFirstByLastNameAndDateOfBirthOrderByIdAsc` (dedup rule 2) |
| `PatientSourceRecordRepository` | `findBySourceSystemIdAndSourceMrn` (dedup rule 1); a `@Query` with `JOIN FETCH` that loads source records together with their source system |
| `SourceSystemRepository` | `findByCode` |
| `IngestionBatchRepository` | `findAll(Pageable)` overridden with `@EntityGraph` to load each batch's source system in the same query |
| `IngestionErrorRepository` | `findByBatchId(id, pageable)` |

*Where is the SQL?* Spring Data generates it at startup from the method names (**derived queries**). `findByMrn` becomes `SELECT … WHERE mrn = ?`. `JpaRepository` also supplies `save`, `findById`, `findAll`, `existsById` and more for free.

*What is the N+1 problem?* Loading 20 batches and then touching each batch's source system would run 1 query plus 20 more. `JOIN FETCH` and `@EntityGraph` load both in one query.

### Parsers (`parser/`): read a file, return raw records

| Class | What it does | Likely question |
|---|---|---|
| `RecordParser` | Interface: `parse(InputStream)` and `getSupportedFormat()`. | *How would you add a fourth format?* One new `@Component` implementing this. Nothing else changes: open/closed principle. |
| `CsvRecordParser` | Apache Commons CSV, maps columns by header name, checks required columns, line number = record number + 1 (the header is line 1). | *Why not `line.split(",")`?* A quoted value like `"Hyderabad, Old City"` contains a comma. |
| `JsonRecordParser` | Jackson `readTree`, maps `patientId → mrn`, `given_name → firstName`, `family_name → lastName`, `dob`, `sex`, `contact`, nested `address`. | *Why inject `ObjectMapper`?* Reuse Spring's configured instance; `ObjectMapper` is thread-safe and expensive to create. |
| `Hl7RecordParser` | Reads PID segments: `\|` separates fields, `^` separates components, positions 3/5/7/8/11/13. Ignores MSH, PV1, OBX, Z-segments. | *Why `split("\\\|", -1)`?* Without `-1`, trailing empty fields are dropped and positions shift. *Why does PID-5 give last name first?* That's the HL7 convention: family^given. |
| `ParserFactory` | Spring injects **all** `RecordParser` beans as a `List`; the factory builds an `EnumMap` by format and fails at startup on duplicates. | *How did the factory learn about the parsers without you registering them?* Spring collects every bean of that type into the list. |

### Normalisers (`normalizer/`): make values comparable

| Class | Input → output |
|---|---|
| `DateNormalizer` | `1998-03-15`, `15/03/1998`, `19980315` → `LocalDate`. `STRICT` resolver so `1998-02-30` is rejected instead of becoming 28 Feb. Returns `null` rather than throwing: a bad date is data to report, not a crash. |
| `GenderNormalizer` | `F`, `f`, `Female` → `FEMALE`. `isRecognised()` lets the validator tell "not given" apart from "gibberish". |
| `PhoneNormalizer` | `+91-9876543210` → `9876543210`. Length is checked by the validator, not here. |
| `NameNormalizer` | `FATIMA` → `Fatima`, `mary ann` → `Mary Ann`. Known limitation: `McDonald` → `Mcdonald`. |
| `PatientRecordNormalizer` | Runs a validated DTO through all four and builds a `Patient`. |

*Why separate classes instead of private methods?* Each has one job and can be unit-tested with no Spring and no database.

### Validation (`validator/`)

| Class | What it does |
|---|---|
| `PatientRecordValidator` | Runs every rule and returns **every** failure. A `Clock` is passed in so tests can pin "today" for the future-date rule. |
| `ValidationResult` | A `record` holding the list of errors; `isValid()` means the list is empty. |
| `FieldError` | A `record` of `(field, message)`. |

*What is a Java `record`?* An immutable data class; the compiler generates the constructor, accessors, `equals`, `hashCode` and `toString`.

### Services (`service/`): the logic

| Class | What it does | Likely question |
|---|---|---|
| `IngestionService` | Orchestrates parse → validate → normalise → dedup for one file and fills in the batch. **Not** `@Transactional` as a whole. | *If 3 of 100 records fail, what happens to the other 97?* They're saved. Each record has its own transaction; the 3 are logged with line numbers. |
| `DeduplicationService` | Rule 1 (same source + MRN), rule 2 (other source + last name + DOB), else new patient. Conflict policy: keep existing, fill gaps, log. `@Transactional` per record. | *Why is this a separate bean?* `@Transactional` works through a proxy; a self-invoked private method would get no transaction. *What's the weakness?* Two people with the same surname and DOB would merge. |
| `DeduplicationResult`, `MatchType` | What happened to a record: `NEW_PATIENT`, `SAME_SOURCE_MRN` or `CROSS_SOURCE_DEMOGRAPHICS`, plus the conflict count. | |
| `PatientService`, `BatchService`, `SourceSystemService` | Read-only queries. `@Transactional(readOnly = true)` at class level; mapping to DTOs happens inside the transaction because `open-in-view` is off. | *What does `readOnly` do?* Hibernate skips dirty checking, and the database can optimise. |

### DTOs and mappers (`dto/`, `mapper/`)

`PatientResponse`, `SourceRecordResponse`, `IngestionResponse`, `BatchResponse`, `IngestionErrorResponse`, `SourceSystemResponse`, `ErrorResponse` and `PageResponse` are records returned by the API. `PatientMapper` and `BatchMapper` convert entities into them.

*Why not return entities directly?* (1) You'd expose internal columns. (2) Renaming a column would break API clients. (3) Serialising a lazy relationship outside a transaction throws `LazyInitializationException`.

`PatientRecordDTO` is different: it's the *input* side, the raw record a parser produced.

### Controllers (`controller/`): HTTP only

| Class | Endpoints |
|---|---|
| `IngestionController` | `POST /api/v1/ingest`, which returns **201** + `Location` |
| `PatientController` | `GET /patients` (paged), `/patients/{id}`, `/patients/{id}/sources`, `/patients/search?mrn=` |
| `BatchController` | `GET /batches`, `/batches/{id}`, `/batches/{id}/errors` |
| `SourceSystemController` | `GET /source-systems` |

*`@PathVariable` vs `@RequestParam`?* `/patients/{id}` vs `/patients/search?mrn=10001`. *Where does `Pageable` come from?* Spring builds it from `?page=&size=&sort=`; `@PageableDefault` sets defaults.

### Exceptions (`exception/`)

| Class | Status |
|---|---|
| `InvalidRequestException` | 400 |
| `ResourceNotFoundException` | 404 |
| `ParseException` | 422 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`: maps every exception to a status and one JSON error shape. Also handles missing parameters (400), non-numeric IDs (400), bad sort fields (400), unknown URLs (404), oversized files (413), wrong content type (415). Anything else is 500 with a generic message; the detail goes only to the log. |

*Why a runtime exception?* Callers aren't forced to catch it, and the handler deals with it in one place.

### Resources

| File | Purpose |
|---|---|
| `application.properties` | MySQL connection (credentials from `DB_USERNAME` / `DB_PASSWORD`), `ddl-auto=update`, `open-in-view=false`, upload limits, Swagger path |
| `application-h2.properties` | Optional in-memory profile for running without MySQL; also used by the integration test |
| `data.sql` | Seeds the three source systems. `INSERT IGNORE` makes it safe to run on every startup; `defer-datasource-initialization` runs it after Hibernate creates the tables |

### Tests (`src/test/java`)

| Test | Kind | What it proves |
|---|---|---|
| `DateNormalizerTest`, `GenderNormalizerTest`, `PhoneNormalizerTest`, `NameNormalizerTest` | Unit | Every source encoding resolves to one canonical value; impossible dates are rejected |
| `PatientRecordValidatorTest` | Unit | **All** errors are collected (5 bad fields → 5 errors), future DOB rejected |
| `CsvRecordParserTest`, `JsonRecordParserTest`, `Hl7RecordParserTest` | Unit | Field mapping, line numbers, quoted commas, truncated HL7, `\r` separators, trailing empty fields |
| `ParserFactoryTest` | Unit | Right parser per format; duplicate registration rejected |
| `DeduplicationServiceTest` | Unit, **Mockito** | Each matching rule and the conflict policy, with mocked repositories |
| `PatientServiceTest` | Unit, **Mockito** | 404 behaviour without a database |
| `IngestionApiIntegrationTest` | Integration, **MockMvc + H2** | The whole stack over HTTP: all three feeds, cross-source matching, error reports, idempotent re-upload, every error status code |

*Unit vs integration test?* A unit test checks one class in isolation (Mockito fakes its dependencies). An integration test starts Spring and checks the pieces working together.

*What does `@Mock` do?* It creates a fake whose behaviour you script with `when(...).thenReturn(...)` and check with `verify(...)`. That's only possible because services take their dependencies through the **constructor**.

### Tooling

| File | Purpose |
|---|---|
| `tools/generate_test_data.py` | Generates the three sample feeds: 200 synthetic patients across 270 records, overlapping sources, ~15% deliberate defects, and deliberate phone and date-of-birth conflicts. Fixed seed, so the output is always the same. |
| `sample-data/` | The generated feeds, ready to upload (200 patients, 270 records). |
| `postman/MedBridge.postman_collection.json` | Every endpoint in demo order, with Postman test scripts checking the status codes, including the 400/404/422 cases. |

---

## 3. Five questions to answer without notes

1. **How do you know two records are the same patient?** Rule 1 and rule 2, and the weakness of rule 2.
2. **Two sources disagree on a birth date. What happens?** Keep existing, log the conflict, never overwrite silently. Why not last-write-wins? Upload order would decide the truth.
3. **Why log failed records instead of discarding them?** Silent data loss is unacceptable in healthcare; someone must be able to say which records failed and why.
4. **3 of 100 records fail: commit 97 or roll back 100?** Commit 97: one transaction per record, errors logged with line numbers. The exception is a file that can't be parsed at all.
5. **How would you add a fourth format?** One new class implementing `RecordParser`. The factory picks it up; no existing code changes.

Follow-ups to be ready for: why DTOs; why the raw payload isn't on `patient`; what happens on re-upload (nothing new, rule 1 matches everything); how this scales (DECISIONS.md, section 14); what `@Transactional` does and why it needs a separate bean here.
