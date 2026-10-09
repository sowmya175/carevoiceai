# CareVoice AI — Phase 1 Backend MVP

A purpose-built daily patient monitoring agent foundation.

## What this version does

- Creates a patient and monitoring plan.
- Starts a daily monitoring session.
- Accepts patient text or a finished voice recording.
- Extracts a small set of clinical facts.
- Stores session state in PostgreSQL.
- Applies deterministic safety/escalation rules.
- Chooses the next follow-up question.
- Marks sessions completed or ready for clinician review.

> This MVP is an engineering prototype, not a medical device and not a diagnostic system. Escalation thresholds are placeholders and must be defined/validated by qualified clinical stakeholders before real-world use.

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- PostgreSQL
- Maven

## Start PostgreSQL

```bash
docker compose up -d
```

## Run the backend

```bash
mvn spring-boot:run
```

Backend: `http://localhost:8080`

## Demo flow

Patient sign-in uses a server session. The `JSESSIONID` cookie is HttpOnly.
Mutating requests also send the CSRF token from `GET /api/auth/csrf` in the
`X-CSRF-TOKEN` header. The React app does this with `credentials: "include"`
and keeps the CSRF token in memory only.

### 1. Register and sign in

```bash
curl -c cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: TOKEN" \
  -d '{"username":"ada01","password":"correct-horse-battery","fullName":"Ada Lovelace","medicalCondition":"Recovery","timezone":"America/New_York"}'
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/login/patient \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: TOKEN" \
  -d '{"username":"ada01","password":"correct-horse-battery"}'
curl -b cookies.txt -c cookies.txt http://localhost:8080/api/auth/csrf
```

Login replaces the CSRF token, so load it again before the next POST.
`POST /api/patients` is closed. Patient registration always creates role `PATIENT`
and does not attach the account to an existing patient row. Doctor accounts are
not publicly registered. For a local demo clinician, set
`CAREVOICE_DEMO_CLINICIAN_ENABLED`, `CAREVOICE_DEMO_CLINICIAN_USERNAME`, and
`CAREVOICE_DEMO_CLINICIAN_PASSWORD` in the environment. The password is hashed
once and is not overwritten when that username already exists. Doctor sign-in
is `POST /api/auth/login/clinician`.

### 2. Start today's check-in

One check-in exists per patient per patient-local calendar date. `Patient.timezone` decides the date. `GET /api/me/check-in/today` returns that status. `POST /api/me/check-in/today` returns the session for that date or creates it. `POST /api/monitoring/me/sessions` uses the same path, so a second start on the same local date does not create another session.

An unfinished check-in from an earlier local date is continued first. The next local date can start only after that session is completed or ready for review. Sessions saved before this rule can have a null `check_in_date`. They remain in history and are not rewritten or counted in the daily uniqueness rule.

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/me/check-in/today \
  -H "X-CSRF-TOKEN: NEW_TOKEN"
```

A patient can set one daily reminder time. New patients start with in-app reminders enabled at 08:00 in `Patient.timezone`. That default is not consent for text messages, email, or push notifications. The scheduler stays off unless `CAREVOICE_REMINDERS_ENABLED=true`. It sends at most one daily check-in reminder per patient-local date, from the reminder time through the configured window (default 4 hours). A later timezone change does not rewrite older reminder dates. If the new local date already has a reminder, another one is not sent.

Assume the returned session ID is `1`.

### 3. Send the patient's first statement

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/monitoring/sessions/1/messages \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: NEW_TOKEN" \
  -d '{"transcript":"I feel dizzy today and my pain is 6 out of 10."}'
```

The agent will extract the facts, calculate risk, and return the next follow-up question.

### 4. Continue the agent loop

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/monitoring/sessions/1/messages \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: NEW_TOKEN" \
  -d '{"transcript":"I did not faint or pass out."}'
```

Repeat until the response says the check-in is complete or ready for review.

## Architecture seam for language understanding

`ClinicalExtractionService` reads one patient message and returns structured facts. The monitoring agent depends only on that interface.

- `CAREVOICE_AI_ENABLED=false` (the default) uses `RuleBasedClinicalExtractionService`.
- `CAREVOICE_AI_ENABLED=true` uses `GeminiClinicalExtractionService`, with the rule-based extractor as a runtime fallback if the provider call fails.
- `QuestionPlannerAgent` chooses the next clinical field and a fallback question deterministically.
- `CAREVOICE_ADAPTIVE_QUESTIONS_ENABLED=false` (the default) shows that fallback question. Set it to `true` together with `CAREVOICE_AI_ENABLED=true` to let Gemini rephrase the already chosen question. Gemini does not choose the field.
- `EscalationEngine` remains the only component that assigns risk.

Groq hosts Whisper for speech-to-text. Gemini extracts clinical facts from that text. Java owns the session workflow, missing-information analysis, escalation, and question selection. React is the patient check-in experience.

Copy `.env.example` for the variable names. Do not commit a real `GOOGLE_API_KEY` or `GROQ_API_KEY`. `.env` is gitignored.

```powershell
$env:CAREVOICE_AI_ENABLED="false"
mvn spring-boot:run
```

```powershell
$env:GOOGLE_API_KEY="YOUR_REAL_KEY"
$env:CAREVOICE_AI_ENABLED="true"
$env:GEMINI_MODEL="gemini-3.8-flash"
mvn spring-boot:run
```

Live Gemini calls are skipped during `mvn test` unless `CAREVOICE_RUN_GEMINI_INTEGRATION_TESTS=true`.

Recorded voice notes are a second input channel. The default transcription provider is Groq-hosted Whisper (`whisper-large-v3-turbo`). The transcript is passed unchanged to the same `ClinicalMonitoringAgent` used by text. `GEMINI_MODEL` stays the clinical extraction model and is not used for transcription.

```powershell
$env:GROQ_API_KEY="YOUR_REAL_GROQ_KEY"
$env:CAREVOICE_VOICE_ENABLED="true"
$env:CAREVOICE_VOICE_PROVIDER="groq"
$env:GROQ_WHISPER_MODEL="whisper-large-v3-turbo"
$env:CAREVOICE_VOICE_LANGUAGE="en"
$env:CAREVOICE_AI_ENABLED="false"
mvn spring-boot:run
```

To use Gemini for clinical extraction as well:

```powershell
$env:GOOGLE_API_KEY="YOUR_REAL_GOOGLE_KEY"
$env:CAREVOICE_AI_ENABLED="true"
$env:GEMINI_MODEL="gemini-3.8-flash"
```

`CAREVOICE_VOICE_PROVIDER=gemini` keeps `GeminiAudioTranscriptionService` available for comparison. It uses `GEMINI_TRANSCRIPTION_MODEL` and `GOOGLE_API_KEY`, not the Whisper model.

```powershell
curl.exe `
  -X POST `
  -F "audio=@sample.wav" `
  http://localhost:8080/api/monitoring/sessions/1/voice
```

The response includes the transcript and the monitoring agent's next question. Raw audio is not stored. Live Groq tests stay skipped unless `CAREVOICE_RUN_GROQ_AUDIO_TESTS=true`, `GROQ_API_KEY` is set, and `CAREVOICE_TEST_AUDIO_PATH` points at a local non-patient recording. The Gemini transcription comparison test stays skipped unless `CAREVOICE_RUN_GEMINI_AUDIO_TESTS=true`.

Realtime microphone streaming, text-to-speech, and voice biomarkers are not implemented. Escalation stays deterministic.

## Longitudinal patient analysis

`GET /api/patients/{patientId}/longitudinal-summary?limit=30` returns deterministic,
read-only summaries of the latest completed/reviewable check-ins (limit 1–100).
See [API semantics and data sources](docs/longitudinal-analysis.md) and the
[example response](docs/longitudinal-summary.example.json). Existing detailed
history endpoints continue to provide the original Q&A.

## Important design rule

Groq-hosted Whisper turns speech into text. Gemini turns that text into structured clinical facts. `QuestionPlannerAgent` and `EscalationEngine` stay deterministic so escalation can be reviewed, tested, versioned, and audited.
