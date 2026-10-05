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
