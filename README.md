# SmartQueue

SmartQueue is a clinic queue management system with:
- **Backend**: Spring Boot + PostgreSQL (Flyway) + Redis + WebSocket + JWT Auth
- **Frontend**: React (Create React App) served via Nginx in Docker
- **Patient Bot**: Telegram webhook flow for guided token booking + live updates
- **Observability**: Prometheus + Grafana (Docker Compose)

## Repository Structure (quick)
- `backend/` — Spring Boot API, queue logic, Telegram webhook, Redis state, Flyway migrations
- `smartqueue-frontend/` — React UI (Doctor / Reception / Admin / Display board)
- `infra/` — Prometheus + Grafana provisioning
- `docker-compose.yml` — local full-stack (Postgres, Redis, Backend, Frontend, Prometheus, Grafana, Ollama)

Detailed docs:
- `docs/USER_MANUAL.md`
- `docs/DEPLOYMENT_INSTRUCTIONS.md`
- `docs/CODE_DIRECTORY_STRUCTURE.md`
- `docs/DATABASE_SCRIPTS.md`
- `docs/API_REFERENCE.md`
- `docs/PROJECT_SUBMISSION_INDEX.md`

## Quick Start (Docker Compose)
1. Create env file:
   - Copy `/.env.example` → `/.env`
   - Add required secrets (at minimum `JWT_SECRET`)
2. Start stack:
   - `docker compose up -d`
3. Open:
   - Frontend: `http://localhost:3000`
   - Backend: `http://localhost:8080/actuator/health`
   - Prometheus: `http://localhost:9090`
   - Grafana: `http://localhost:3001` (admin / admin)

## Local Development (without Docker)
- Backend: `cd backend && ./mvnw spring-boot:run`
- Frontend: `cd smartqueue-frontend && npm install && npm start`

## Common Ports
- 5432 Postgres, 6379 Redis, 8080 Backend, 3000 Frontend
- 9090 Prometheus, 3001 Grafana, 11434 Ollama

