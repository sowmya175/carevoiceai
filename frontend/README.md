# CareVoice check-in

Patient check-in for the CareVoice Spring Boot API. The browser records a voice note and sends it to Spring Boot. Gemini is called only by the backend.

```powershell
npm install
npm run dev
```

The app runs at `http://localhost:5173`. Set `VITE_API_BASE_URL` in `.env` (see `.env.example`) to the Spring Boot origin, usually `http://localhost:8080`.

```powershell
npm test
npm run build
```

The patient navigation switches between Today's Check-In and Health History without
a router dependency. The current check-in stays mounted to preserve unsent drafts;
navigation is disabled while recording or submitting an answer.

The signed-in patient comes from `GET /api/auth/me` after login and on refresh.
History never creates a patient. The summary is loaded from
`/api/patients/{patientId}/longitudinal-summary?limit=30`; selected Q&A is
loaded from `/api/monitoring/sessions/{sessionId}/history`.

Trends use plain CSS and an accessible SVG pain chart with a dated text alternative.
Unknown entries are shown explicitly. Temperature values have no assumed units.
Risk codes, extracted JSON, clinical notes, and provider metadata are not rendered.

Responses remain in React memory; history requests use `cache: "no-store"`.
localStorage keeps only the active `sessionId` so a check-in can resume. It does
not store the patient id, email, name, condition, timezone, password, or a token.
Every API call uses `credentials: "include"` so the browser sends the HttpOnly
session cookie. JavaScript does not read that cookie. A POST first loads
`GET /api/auth/csrf` and sends the returned token in the `X-CSRF-TOKEN` header.
That token stays in memory and is discarded after login and logout. Completion
provides links to the current responses and health history. Progress shows
actual saved responses, without an estimated total or percentage. Layouts adapt
at 900px and 600px.
