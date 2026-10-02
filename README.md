# MedBridge — Healthcare Patient Record Integration Service

Three healthcare systems send patient records in three different formats: a diagnostic **lab** exports CSV, a **clinic** patient portal sends JSON, and a **hospital** admissions system sends **HL7 v2**. They use different field names, date formats and codes for the same people.

MedBridge ingests all three, normalises them into **one canonical MySQL schema**, validates every field, recognises when two systems describe the **same patient**, and logs every rejected record with its **line number and reason**, so nothing is silently dropped.

```
parse  →  normalise  →  validate  →  deduplicate  →  persist
```

**Stack:** Java 17 · Spring Boot 3.3 · Spring Data JPA / Hibernate · MySQL 8 · Maven · JUnit 5 · Mockito · Swagger (springdoc-openapi)

---

## The problem, in three lines

The same patient, as each system sends them:

```
CSV   10001,Zoya,Fatima,1998-03-15,F,9876543210,Hyderabad,TS,500001

JSON  { "patientId": "10001", "given_name": "Zoya", "family_name": "Fatima",
        "dob": "15/03/1998", "sex": "Female", "contact": "+91-9876543210", ... }

HL7   PID|1||10001^^^HOSP^MR||FATIMA^ZOYA^||19980315|F|||Hyderabad^TS^500001||9876543210
```

| Field | LAB (CSV) | CLINIC (JSON) | HOSPITAL (HL7) | Canonical |
|---|---|---|---|---|
| Patient ID | `mrn` | `patientId` | PID-3, component 1 | `mrn` |
| First name | `first_name` | `given_name` | PID-5, component 2 | `Zoya` |
| Last name | `last_name` | `family_name` | PID-5, component 1 (family name first) | `Fatima` |
| Date of birth | `1998-03-15` | `15/03/1998` | `19980315` | `LocalDate 1998-03-15` |
| Gender | `F` | `Female` | `F` | `FEMALE` |
| Phone | `9876543210` | `+91-9876543210` | `9876543210` | `9876543210` |
| Name casing | Mixed | Mixed | UPPERCASE | Titlecase |

**Why one person appears in three feeds:** care crosses organisations. A patient visits a dermatology clinic, gets blood work at a lab, and is admitted to a hospital. Three independent organisations, three registrations, three databases that don't share data. The clinic wants all of it in one chart.

---

## Architecture

```
  lab_export.csv ──┐                       ┌─ CsvRecordParser
  clinic_feed.json ─┼─► POST /api/v1/ingest ─► ParserFactory ─┼─ JsonRecordParser    (RecordParser interface)
  hospital_adt.hl7 ─┘                       └─ Hl7RecordParser
                                                    │
                                                    ▼
                                       PatientRecordDTO  (all fields still raw Strings)
                                                    │
                                                    ▼
                                       PatientRecordValidator ──── invalid ──►  IngestionError rows
                                       (collects EVERY problem)                  (line, field, reason, raw text)
                                                    │ valid
                                                    ▼
                                       PatientRecordNormalizer
                                       Date · Gender · Phone · Name
                                                    │
                                                    ▼
                                       DeduplicationService  (one transaction per record)
                                       rule 1: same source + same MRN
                                       rule 2: other source + same last name & DOB
                                                    │
                                                    ▼
                               MySQL:  patient ◄── patient_source_record ──► source_system
                                       ingestion_batch ◄── ingestion_error
```

### Data model (five entities)

| Table | Holds |
|---|---|
| `patient` | One canonical row per real person |
| `source_system` | LAB / CLINIC / HOSPITAL and the format each sends (seeded by `data.sql`) |
| `patient_source_record` | Links a patient to each source that described them, with that source's own MRN and the **raw original payload** (the audit trail) |
| `ingestion_batch` | One file upload: status, timings, total / saved / failed / new / matched / conflict counts |
| `ingestion_error` | One rejected field of one record: line number, field, reason, raw line |

Indexes: `patient(last_name, date_of_birth)` for the deduplication lookup, `patient(mrn)` for search, and a unique constraint on `patient_source_record(source_system_id, source_mrn)` that enforces "one MRN = one person within a source" in the database itself.

### Package layout

```
src/main/java/com/medbridge/
├── controller/   IngestionController, PatientController, BatchController, SourceSystemController
├── service/      IngestionService, DeduplicationService, PatientService, BatchService, SourceSystemService
├── parser/       RecordParser (interface), CsvRecordParser, JsonRecordParser, Hl7RecordParser, ParserFactory
├── normalizer/   DateNormalizer, GenderNormalizer, PhoneNormalizer, NameNormalizer, PatientRecordNormalizer
├── validator/    PatientRecordValidator, ValidationResult, FieldError
├── entity/       Patient, SourceSystem, PatientSourceRecord, IngestionBatch, IngestionError (+ enums)
├── repository/   one Spring Data JPA repository per entity
├── dto/          request/response records, kept separate from entities
├── mapper/       entity → DTO
├── exception/    ParseException, ResourceNotFoundException, InvalidRequestException, GlobalExceptionHandler
└── config/       OpenApiConfig (Swagger)
```

See **[CODE_WALKTHROUGH.md](CODE_WALKTHROUGH.md)** for what every class does and **[DECISIONS.md](DECISIONS.md)** for why it was built this way.

---

## Running it

### Prerequisites
- **JDK 17 or newer**
- **Maven 3.9+** (IntelliJ IDEA bundles it, so a separate install is optional)
- **MySQL 8** (or skip it and use the H2 profile below)

### Option A: with MySQL (default)

1. Start MySQL. The `medbridge` database is created automatically on first run.
2. Give the app your MySQL credentials, either by editing the defaults in `src/main/resources/application.properties` or with environment variables:
   ```bash
   # macOS / Linux
   export DB_USERNAME=root
   export DB_PASSWORD=your_password
   # Windows PowerShell
   $env:DB_USERNAME="root"; $env:DB_PASSWORD="your_password"
   ```
3. Run:
   ```bash
   mvn spring-boot:run
   ```

### Option B: no MySQL (in-memory H2)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
Data disappears when the app stops. The H2 console is at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:medbridge`, user `sa`, empty password).

### In IntelliJ IDEA
Open the folder → wait for Maven to import → run `MedbridgeApplication`. For H2, set *Active profiles* to `h2` in the run configuration.

### Tests
```bash
mvn test
```
Unit tests (JUnit 5 + Mockito) cover the parsers, normalisers, validator, deduplication rules and patient service. `IngestionApiIntegrationTest` drives the whole stack over HTTP with MockMvc against an in-memory H2 database, using the same three sample feeds.

---

## Demo

Open **Swagger UI: http://localhost:8080/swagger-ui.html**. Every endpoint has a *Try it out* button, including file upload.

Or import **`postman/MedBridge.postman_collection.json`** into Postman: 16 requests in demo order, each with test scripts that check the status code, including the error cases (404, 400, 422). Set Postman's *Settings → General → Working directory* to this project folder so the file uploads find `sample-data/`.

Or with curl, from the project folder (on Windows use `curl.exe`):

```bash
curl -F "file=@sample-data/lab_export.csv"   -F "sourceSystem=LAB"      http://localhost:8080/api/v1/ingest
curl -F "file=@sample-data/clinic_feed.json" -F "sourceSystem=CLINIC"   http://localhost:8080/api/v1/ingest
curl -F "file=@sample-data/hospital_adt.hl7" -F "sourceSystem=HOSPITAL" http://localhost:8080/api/v1/ingest

curl http://localhost:8080/api/v1/batches/3/errors      # what failed in the hospital feed, and why
curl http://localhost:8080/api/v1/patients?size=5       # canonical patients
curl http://localhost:8080/api/v1/patients/{id}/sources # the original CSV/JSON/HL7 behind one patient
```

### What the sample data produces

`sample-data/` is generated by `tools/generate_test_data.py` (fixed seed, fully synthetic, no real patient data): **200 invented patients** spread across **270 records**, each patient registered with one, two or all three systems, with **~15% of records deliberately broken** and a few deliberate conflicts.

Ingesting LAB → CLINIC → HOSPITAL in that order gives:

| | LAB (CSV) | CLINIC (JSON) | HOSPITAL (HL7) | Total |
|---|---|---|---|---|
| Records in file | 93 | 94 | 83 | **270** |
| Rejected (logged with reason) | 4 | 19 | 17 | **40 (14.8%)** |
| Saved | 89 | 75 | 66 | **230** |
| → new canonical patient | 82 | 56 | 43 | **181** |
| → matched an existing patient | 7 | 19 | 23 | **49** |
| Field conflicts logged | 7 | 5 | 0 | **12** |

- **37 patients** end up reconciled from two or more systems, **5** from all three.
- Rejections by field: date of birth 12, gender 10, last name 8, MRN 6, phone 4.
- The 12 conflicts: 5 LAB re-sends with a new phone and 2 with a new date of birth (same MRN, matched by rule 1), plus 5 CLINIC records whose phone disagrees with the LAB's (matched by rule 2). In every case the existing value is kept and the disagreement is logged.
- Uploading the same file again creates **0** new patients.

Example: one canonical patient and the three records behind it (`GET /api/v1/patients/{id}/sources`):

```
LAB       10103,Vikram,Fatima,1953-12-12,M,9806232010,Bengaluru,KA,560001
CLINIC    {"patientId":"10103","given_name":"Vikram","family_name":"Fatima","dob":"12/12/1953","sex":"Male","contact":"+91-9933002893",...}
HOSPITAL  PID|1||10103^^^HOSP^MR||FATIMA^VIKRAM^||19531212|M|||Bengaluru^KA^560001||9806232010
```
The clinic's phone number disagrees with the lab's, so the existing value is kept and the conflict is logged.

Example rejected records (`GET /api/v1/batches/3/errors`):

```
line 29  [phone]        Phone must have 10 digits after normalisation, found 5 ('12345')
line 41  [mrn]          MRN is required
line 44  [gender]       Unrecognised gender code: 'Z'
line 56  [dateOfBirth]  Unrecognised or impossible date: '19981345' (expected yyyy-MM-dd, dd/MM/yyyy or yyyyMMdd)
```

To regenerate the data: `python3 tools/generate_test_data.py`

---

## API

| Method | Path | Returns |
|---|---|---|
| `POST` | `/api/v1/ingest` | multipart `file` + `sourceSystem` → **201** with batch summary and `Location` header |
| `GET` | `/api/v1/patients` | paged canonical patients (`?page=0&size=20&sort=lastName,asc`) |
| `GET` | `/api/v1/patients/{id}` | one patient, **404** if unknown |
| `GET` | `/api/v1/patients/{id}/sources` | the original record from each contributing system |
| `GET` | `/api/v1/patients/search?mrn=` | patients with that MRN (a list: MRNs are not unique across systems) |
| `GET` | `/api/v1/batches` | ingestion history, newest first |
| `GET` | `/api/v1/batches/{id}` | one batch with its counts |
| `GET` | `/api/v1/batches/{id}/errors` | every rejected field: line number, field, reason, raw line |
| `GET` | `/api/v1/source-systems` | the configured systems and their formats |

### Status codes

| Code | When |
|---|---|
| `200` | successful read |
| `201` | file ingested (even if some records were rejected: see `failureCount`) |
| `400` | unknown source system, empty file, missing or invalid parameter |
| `404` | patient, batch or URL does not exist |
| `413` | file larger than 10 MB |
| `415` | upload was not `multipart/form-data` |
| `422` | the file itself cannot be parsed (malformed JSON, missing CSV columns); the batch is still recorded as `FAILED` |
| `500` | unexpected error; the client gets a generic message, the detail goes to the server log |

Every error has the same shape:

```json
{
  "timestamp": "2026-10-01T14:32:11.207",
  "status": 404,
  "error": "Not Found",
  "message": "Patient not found with id 999",
  "path": "/api/v1/patients/999"
}
```

---

## Deliberately out of scope

- **Authentication**: a real deployment would sit behind the platform's identity layer.
- **A frontend**: in production, feeds arrive over SFTP, MLLP or webhooks, not a browser. Swagger UI covers the demo.
- **Asynchronous processing**: files are processed within the request. At scale, uploads would go onto a queue and be processed by workers (see DECISIONS.md, "How this would scale").
- **FHIR**: the modern JSON/REST healthcare standard. Adding it means one new `RecordParser` class, with no changes to existing code.
