# Code Directory Structure

## Top Level
- `backend/` — Spring Boot backend (REST + WebSocket + Redis + Flyway + Telegram webhook)
- `smartqueue-frontend/` — React frontend (Doctor/Reception/Admin/Display views)
- `infra/` — Prometheus + Grafana provisioning
- `docker-compose.yml` — full stack for local/dev (Postgres, Redis, Backend, Frontend, Prometheus, Grafana, Ollama)

## Backend (`backend/`)
- `backend/pom.xml` — Maven build + dependencies
- `backend/src/main/java/com/smartqueue/backend/`
  - `controller/` — HTTP entrypoints (Auth, Queue, Doctor, Reception, Admin, Telegram webhook)
  - `service/` — domain logic (queue management, AI triage, Telegram messaging)
  - `repository/` — Spring Data repositories (Postgres)
  - `entity/` — JPA entities (database models)
  - `security/` — JWT + security configuration
  - `websocket/` — WS config + messaging helpers
  - `metrics/` — Micrometer/Prometheus metric plumbing
  - `classifier/` — symptom triage / rule-based classifier (AI pipeline)
  - `idempotency/`, `lock/` — token/idempotency & concurrency helpers
  - `dto/` — request/response DTOs
  - `enums/` — enums used across the system
- `backend/src/main/resources/`
  - `application.properties` — environment-driven config
  - `db/migration/` — Flyway SQL migrations (see `docs/DATABASE_SCRIPTS.md`)

## Frontend (`smartqueue-frontend/`)
- `smartqueue-frontend/src/` — React source
  - `pages/` — main screens (DoctorPanel, ReceptionPanel, AdminPage, DisplayBoard, LoginPage)
  - `auth/` — auth context + token storage
  - `services/` — API client wrappers
  - `websocket/` — STOMP/SockJS clients for live updates
  - `components/` — reusable UI building blocks
- `smartqueue-frontend/public/` — static public assets
- `smartqueue-frontend/nginx.conf` — container web server config
- `smartqueue-frontend/Dockerfile` — build + serve the frontend in Docker

## Infra (`infra/`)
- `infra/prometheus/prometheus.yml` — Prometheus scrape config
- `infra/grafana/provisioning/datasources/prometheus.yml` — Grafana datasource provisioning

