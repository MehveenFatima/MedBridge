# Design Decisions

Each entry: what was decided, why, and what the alternative would cost.

---

## 1. One canonical schema, many formats converted at the boundary

**Decision.** Every feed is converted into the same `Patient` shape as it arrives. Nothing downstream knows or cares whether a patient came from CSV, JSON or HL7.

**Why.** Healthcare formats differ because nobody can mandate one. Hospitals run systems that predate any standard, HL7 v2 allows optional fields and vendor-specific segments, and newer portals speak JSON. The receiving system adapts to its sources, not the other way round. One internal model with conversion at the edge is the only design where adding a source doesn't ripple through the codebase.

---

## 2. Parsers sit behind an interface, chosen by a factory

**Decision.** `RecordParser` is an interface with `parse()` and `getSupportedFormat()`. `ParserFactory` receives every implementation from Spring as a `List<RecordParser>` and maps them by format.

**Why.** Open/closed principle. Supporting FHIR means writing one new `@Component` class; the factory, the ingestion service and the existing parsers don't change. The factory never names a concrete parser, and it throws at startup if two parsers claim the same format rather than letting one silently shadow the other.

**Alternative.** A `switch` on format inside one big parser class: every new format edits working code.

---

## 3. Parsers return raw Strings; validation is a separate step

**Decision.** `PatientRecordDTO` holds every field as a `String`, exactly as read. Parsers never judge values.

**Why.** If a parser threw on `"BAD_DATE"`, it could only report the first problem and would have no clean way to say which line failed. Keeping parsing (can I read this file?) separate from validation (is this record acceptable?) means a malformed value becomes a precise error report, and only a malformed *file* fails the upload.

---

## 4. Validation collects every error in a record instead of stopping at the first

**Decision.** `PatientRecordValidator` runs every rule and returns all failures. A record with a blank MRN, an impossible date and a short phone number produces three error rows.

**Why.** Someone fixing a broken feed needs the whole picture in one pass. Fail-fast means fix one thing, re-send, discover the next, re-send again. In a hospital that loop costs hours.

**Why a hand-written validator rather than Bean Validation annotations (`@NotBlank`, `@Past`) on the DTO?** Bean Validation also collects every violation, but these rules depend on the normalisers (a date is valid if it parses in *any* of three formats; a phone is judged *after* stripping `+91-`), and every error must carry the record's line number and raw text for the error log. A plain class that calls the normalisers keeps all of that in one readable place and is unit-tested without Spring.

**Rules:** MRN and last name required; date of birth required, parseable and not in the future; gender optional but must be a known code if present; phone optional but must be 10 digits after normalisation if present; lengths capped at the column sizes so bad data never reaches the database as an exception.

---

## 5. Failed records are logged, never discarded

**Decision.** Every rejected record becomes `ingestion_error` rows holding the line number, field, reason and the original raw text.

**Why.** In healthcare, silent data loss is unacceptable. When a clinic says "we sent 400 records and only 380 arrived", someone has to answer which 20 failed and why without reading server logs. `GET /api/v1/batches/{id}/errors` is that answer.

---

## 6. Transaction boundary: one transaction per record, not per file

**Decision.** `IngestionService.ingest()` is deliberately **not** `@Transactional`. Each accepted record is saved inside `DeduplicationService.resolve()`, which is `@Transactional`, so the patient and its source link commit together or not at all.

**Why.** If 3 records in a file of 100 are bad, the other 97 should land. Rolling back 97 good patients because of 3 malformed ones would keep real patients out of the system until the sender fixes and re-sends the whole file. Partial success plus a precise error report is the norm for healthcare feeds.

**Why the transaction lives in a separate bean.** `@Transactional` works through a Spring proxy. A `@Transactional` private method called from inside `IngestionService` would bypass the proxy and get no transaction at all (the self-invocation trap).

**Trade-off.** The sender cannot assume all-or-nothing. If a source ever needed atomic files, that would be a per-source setting, not the default.

**Whole-file failures** (malformed JSON, missing CSV columns) are different: nothing is saved, the batch is still recorded with status `FAILED` and a reason, and the API returns 422.

---

## 7. Deduplication rules

**Decision.**
1. **Same source, same MRN → same patient.** A source's MRNs are consistent within that source; that's what an MRN is for. Enforced in the database by a unique constraint on `(source_system_id, source_mrn)`.
2. **Different source, same last name + date of birth → same patient.** MRNs mean nothing across sources: the lab's `10001` and the hospital's `10001` can be different people.
3. Otherwise, a new canonical patient.

A match never creates a duplicate `patient` row; it adds a `patient_source_record` link.

**Known weakness, stated up front.** Two different people with the same surname and birth date would be merged. A production matcher (an "enterprise master patient index") adds more fields, scores candidate pairs probabilistically, auto-merges only above a confidence threshold, and sends the uncertain middle to a human for review. That was out of scope; the rule is simple on purpose and its failure mode is known.

**Normalise before matching.** Names are converted to Titlecase before the lookup, so `FATIMA` from HL7 and `Fatima` from CSV compare equal with an exact match. That also lets the query use the `(last_name, date_of_birth)` index; a case-insensitive `UPPER(last_name) = ?` comparison would not.

**Re-sends are idempotent.** Uploading the same file twice matches every record by rule 1 and creates nothing new.

---

## 8. Conflict policy: keep existing, fill gaps, log disagreements

**Decision.** When a matched record disagrees with the stored patient:
- existing value present and different → **keep existing**, log a warning, count it on the batch
- existing value missing → **fill it** from the new source
- never overwrite silently

**Why.** A clinician must not see a patient's date of birth change because a second feed disagreed. Last-write-wins makes the data depend on upload order, which nobody controls. Logging the conflict makes it visible for a person to resolve.

The latest raw payload from each source is kept on its `patient_source_record`, so the original text is always available for review.

---

## 9. Raw payloads live on the link table, not on `patient`

**Why.** One patient can have several source records, each with its own original text. Raw text is for audit and troubleshooting, not for everyday queries, so it doesn't belong on the row every query reads.

---

## 10. HL7 parser written by hand

**Decision.** No HAPI library. Split on `|` for fields and `^` for components, read MSH and PID, ignore every other segment.

**Details that matter:**
- `split("\\|", -1)`: without `-1`, Java drops trailing empty fields and every later position in a positional format shifts.
- A truncated segment returns empty values instead of throwing `ArrayIndexOutOfBoundsException`, so it becomes a validation error for that one record rather than a crashed batch.
- `readLine()` accepts `\r` separators, which real HL7 uses.
- PID-5 puts the **family name first**.

**Why no library.** Only two segments are needed, the mechanics are simple once the format is understood, and understanding the format was the point.

---

## 11. Dates are parsed strictly

**Decision.** `DateNormalizer` tries `uuuu-MM-dd`, `dd/MM/uuuu` and `uuuuMMdd` with `ResolverStyle.STRICT`.

**Why.** Java's default (SMART) resolver quietly turns `1998-02-30` into 28 February. For a date of birth, a wrong date that looks valid is worse than a rejected one.

---

## 12. API design

- **DTOs, never entities, in responses.** The API exposes only what clients need; renaming a column can't silently break clients; lazy JPA relationships never reach the JSON serializer.
- **`spring.jpa.open-in-view=false`.** The database session closes when the service method returns, so all mapping happens inside `@Transactional(readOnly = true)` service methods, and nothing triggers hidden queries during JSON rendering.
- **N+1 avoided where it would occur:** `JOIN FETCH` when loading a patient's source records, `@EntityGraph` on the batch history page.
- **Status codes mean something.** 201 + `Location` on ingest, 404 for missing resources (not 200 with null), 400 for bad requests, 422 for unreadable files, 413 for oversized uploads. One `@RestControllerAdvice` gives every error the same JSON shape, and 500s never leak stack traces or SQL to the client.
- **MRN search returns a list,** because MRNs are not unique across systems.
- **Versioned under `/api/v1`,** so a breaking change can ship as `/api/v2` alongside.

---

## 13. Schema management

`ddl-auto=update` lets Hibernate create the tables, which is convenient while building. A production service would use versioned migrations (Flyway or Liquibase) so schema changes are reviewed, repeatable and reversible.

---

## 14. How this would scale

The service processes a file inside the HTTP request. For millions of records a day:
- accept the upload, store the file, return 202 immediately, and put a message on a queue (Kafka, SQS)
- a pool of stateless workers parses and ingests in parallel, with batched inserts instead of one save per record
- run several instances behind a load balancer, with connection pooling (HikariCP, already the Spring Boot default)
- partition `ingestion_error` and `ingestion_batch` by date, and serve read endpoints from a replica
- move matching to a dedicated master-patient-index service with probabilistic scoring

None of this is built. It is the path from here.

---

## 15. What was left out on purpose

Authentication, a frontend, Docker, CI, message queues and caching. Each would add days and none changes the core problem this project is about: turning inconsistent feeds into trustworthy records. In production, files arrive over SFTP, MLLP or webhooks, and an operations team would want a dashboard of failed batches; the `/batches` and `/errors` endpoints are the data such a dashboard would use.
