# Deployment Instructions

This document covers:
1) Local deployment via Docker Compose (recommended for demos/testing)  
2) Production-style deployment considerations (secrets, TLS, backups, Telegram webhook)

## 1) Local Deployment (Docker Compose)

### Prerequisites
- Docker Desktop (or Docker Engine + Compose)

### Steps
1. Create environment file:
   - Copy `.env.example` → `.env`
2. Set required variables in `.env`:
   - `DB_USERNAME`, `DB_PASSWORD`
   - `JWT_SECRET` (required for login/auth)
   - Optional: clinic scheduling parameters (see `.env.example`)
3. Start the stack:
   - `docker compose up -d`
4. Verify health:
   - Backend health: `http://localhost:8080/actuator/health`
   - Frontend: `http://localhost:3000`

### Included Services / Ports
Defined in `docker-compose.yml`:
- Postgres `:5432`, Redis `:6379`, Backend `:8080`, Frontend `:3000`
- Prometheus `:9090`, Grafana `:3001`, Ollama `:11434`

## 2) Production Deployment (Recommended Approach)

### Recommended Baseline
- Run using Docker Compose (or Kubernetes) with:
  - External TLS reverse proxy (Nginx/Traefik/Caddy)
  - Persistent volumes for Postgres/Redis
  - Environment variables provided by your secret manager

### Required Secrets
Do not commit these values; provide as environment variables at runtime:
- `JWT_SECRET`
- `DB_PASSWORD`
- `TELEGRAM_BOT_TOKEN` (if using Telegram; maps to `telegram.bot.token`)
- `TELEGRAM_BOT_WEBHOOK_BASE_URL` (maps to `telegram.bot.webhook-base-url`)

### Telegram Webhook Setup
Backend endpoint:
- `POST /telegram/webhook`

To register the webhook automatically at startup, configure:
- `telegram.bot.webhook-base-url` (or env `TELEGRAM_BOT_WEBHOOK_BASE_URL`) to your public base URL (e.g., `https://example.com`)
The backend will register:
- `https://example.com/telegram/webhook`

Ensure your reverse proxy forwards `/telegram/webhook` to the backend and allows Telegram IPs (or rate-limits appropriately).

### Database & Migrations
- Flyway runs automatically on backend startup.
- Backup before deploy; never modify applied migration scripts.
- Scripts are documented in `docs/DATABASE_SCRIPTS.md`.

### Monitoring (Optional)
If you run the `prometheus` and `grafana` services:
- Backend exposes Prometheus metrics at `http://<backend-host>:8080/actuator/prometheus`
- Prometheus scrape config: `infra/prometheus/prometheus.yml`
- Grafana datasource provisioning: `infra/grafana/provisioning/datasources/prometheus.yml`

## Build/Run Without Docker (Server Install)

### Backend
- Prereq: Java (compatible with the Spring Boot version in `backend/pom.xml`)
- Run:
  - `cd backend`
  - `./mvnw spring-boot:run`
- Provide env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and optional Redis/Ollama vars.

### Frontend
- Prereq: Node.js + npm
- Run:
  - `cd smartqueue-frontend`
  - `npm install`
  - `npm start`
