# Patient longitudinal summary

`GET /api/patients/{patientId}/longitudinal-summary?limit=30`

The optional limit defaults to 30 and accepts 1 through 100. Invalid limits return
400; a missing patient returns the existing 404 `{"error":"Patient not found: ..."}`
response. An existing patient without eligible sessions receives an empty summary.

## Data source and scope

`MonitoringSession` is the authoritative source of accumulated structured facts:
`MonitoringSessionMerger` applies extracted facts to it, and `CollectedFacts.from`
reads those same fields. `ClinicalNote.extractedFactsJson` contains facts from an
individual turn, not necessarily the session's final value. The analysis therefore
uses session fields directly. It does not read notes, extracted-fact JSON, or transcripts.

Only `COMPLETED` and `READY_FOR_REVIEW` sessions are eligible. The database selects
the most recent eligible sessions by `createdAt DESC, id DESC` before applying the
limit. The selected window is returned oldest first with session ID breaking ties.
Each session is one check-in; multiple sessions on the same day remain separate.

Observation timestamps, `windowStart`, `windowEnd`, and `latestCheckInAt` use session
start time (`createdAt`). They are not symptom-event timestamps. There is no dedicated
completion timestamp in the current model, so `updatedAt` is not presented as one.
All counts and first/last symptom reports refer only to the selected window, not the
patient's lifetime history. The latest check-in refers to the latest eligible session.

## Response semantics

- `pain`: known numeric observations, observation count, first/latest known values,
  and latest minus first. One known value has change zero; no known values produce
  null first/latest/change and an empty observation list. Missing scores are omitted,
  never replaced with zero.
- `sleep`, `appetite`: category counts, unknown count, and an observation for each
  selected session. Missing, blank, or unsupported categories remain unknown/null;
  no normal category or severity score is inferred.
- `medication`: true means taken, false means explicitly missed, and null means
  unknown. All three are counted separately, with chronological observations.
- `symptoms`: dizziness, shortness of breath, and loss of consciousness each have
  explicit true/false/unknown counts and chronological observations. First/last
  reported dates use only true values and are null when there are no true reports.
- `dizzinessOnset`: nonblank stored text is preserved exactly with the session ID and
  start timestamp. No time parsing or mathematical trend is applied.
- `temperature`: known numeric values are preserved without unit assumptions,
  conversion, or fever classification. The current model does not store units.
- `sessions`: compact metadata reuses the existing history session summary DTO:
  session ID, startedAt, stored status/riskLevel, and actual turnCount. Full Q&A stays
  in the existing session-history endpoint.

## Query and behavior boundaries

The read-only transaction performs a patient-existence query, one bounded scalar
projection query, and one grouped turn-count query limited to selected session IDs.
An empty window omits the turn-count query. No entity graphs or transcript columns
are loaded, no per-session queries are issued, and no schema changes are required.

Calculations are deterministic Java. No Gemini calls, diagnosis, treatment advice,
improvement/deterioration labels, or new escalation rules are added. Stored risk
levels are returned as metadata; `EscalationEngine` is unchanged.

## Example

The complete synthetic one-session response is in
[`longitudinal-summary.example.json`](longitudinal-summary.example.json).

## Validation

Run `mvn clean test`. Tests cover empty and partial history, single/multiple sessions,
unknown values, category counts, true-only symptom dates, chronological order,
stable ties, SQL limits, patient isolation, turn counts, API validation, and unchanged
history. H2 integration tests exercise the real Spring Data repository queries and
assert three read statements and zero loaded entities for a 30-session window.
Production PostgreSQL execution/performance remains a live-environment check.
