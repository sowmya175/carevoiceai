# CareVoice backend architecture

CareVoice is a Spring Boot application. Production code under `com.carevoice` follows one direction of dependencies:

```
Controller → Service → Repository → Domain
```

HTTP contracts are DTOs. Mappers convert domain objects into those DTOs. Configuration, exception translation, and third-party clients sit beside that flow.

```
Controller → DTO
Service → Domain, Repository, integration clients
Mapper → Domain, DTO
Repository → Domain
Config → framework and infrastructure beans
Exception → HTTP error translation
Integration → provider HTTP/SDK calls
```

Spring Data repositories are the DAO layer. CareVoice does not introduce a second DAO abstraction unless a genuine persistence abstraction is needed.

## Layers

**Controller** (`com.carevoice.controller`) accepts the request, reads the authenticated principal, calls a service, and returns a response DTO. It does not query repositories or apply clinical rules.

**Service** (`com.carevoice.service`) owns business rules, workflow, transactions, and coordination of repositories. Provider calls go through an abstraction such as `PlanGenerationModel`, with the concrete client chosen by configuration.

**Repository** (`com.carevoice.repository`) is persistence only. Interfaces extend Spring Data `JpaRepository`. Query projections that exist only for JPQL constructor expressions live with the domain types they project.

**Domain** (`com.carevoice.domain`) holds JPA entities, enums, and small value objects. Table and column names stay on the entity. Entities do not call repositories or know about HTTP.

**DTO** (`com.carevoice.dto`) is the JSON contract, grouped by `auth`, `patient`, `clinician`, `monitoring`, `proposal`, and `voice`. A few response records remain nested on the service that assembles them, including the current user, plan assignment, and clinician review views. JSON field names are unchanged.

**Mapper** (`com.carevoice.mapper`) converts entities to DTOs. A mapper does not load data. The service loads the entities and passes them in.

**Config** (`com.carevoice.config`) holds security, CORS, async executors, clocks, and provider bean wiring. The browser origin is `http://localhost:5173` and the API is `http://localhost:8081`. Allowed methods include `GET`, `POST`, `PUT`, and `OPTIONS`.

**Exception** (`com.carevoice.exception`) holds application exceptions and `@RestControllerAdvice` handlers. Clients receive an `error` message. Stack traces, SQL, and provider secrets are not returned. One endpoint keeps a local handler so a malformed query parameter stays HTTP 400 without changing other endpoints.

**Integration** (`com.carevoice.integration.gemini`, `groq`, `vertex`) holds provider clients. Deterministic implementations stay in the service layer. `com.carevoice.training` is developer tooling for the plan dataset and evaluation. It is not a request path.

## Plan proposal example

```
ClinicianPlanProposalController
    → MonitoringPlanProposalService
        → MonitoringPlanProposalRepository
            → MonitoringPlanProposal

MonitoringPlanProposalService
    → PlanGenerationModel
        → GeminiPlanGenerationModel or VertexTunedPlanGenerationModel

MonitoringPlanProposal
    → ProposalViews
        → ProposalView
```

Generation loads a short snapshot, calls the model with no transaction held, then persists in a short transaction. Approval is one transaction. Reassigning a plan does not rewrite an open monitoring session snapshot.

Routing stays on `PlanGenerationModel`:

- plan AI disabled → deterministic generator
- `gemini-base` → temporary Gemini plan model
- `vertex-tuned` → tuned Vertex client

No fine-tuning job is started by the application.
