# SmartQueue

A hospital-grade, multi-tenant clinic queue management platform.

## Tech Stack
| Layer | Technology |
|---|---|
| Backend | Spring Boot 3 · PostgreSQL (Flyway) · Redis · WebSocket/STOMP · JWT |
| Frontend | React (CRA) · STOMP WebSocket · qrcode.react |
| AI Triage | Ollama (llama3) · Rule-based pre-classifier · Resilience4j circuit breaker |
| Telegram | Bot webhook · Guided intake flow · Live status updates |
| Observability | Prometheus · Grafana · Micrometer · Spring Actuator |
| Infrastructure | Docker Compose · Redisson distributed locks |

---

## Feature Inventory (v2 — Final Enhancements)

### Phase 1 — Stability & Cleanup
| # | Feature | Status |
|---|---|---|
| 1 | Removed zombie `QueueController.java` | ✅ |
| 2 | `@Builder.Default` on all `Doctor` + `Token` entity fields | ✅ |
| 3 | `callNext()` throws `ResponseStatusException` (404/409/503) instead of soft failures | ✅ |
| 4 | CORS origins env-var-driven via `CORS_ORIGINS` / `app.cors.allowed-origins` | ✅ |

### Phase 2 — Multi-Tenancy & Reliability
| # | Feature | Status |
|---|---|---|
| 5 | True multi-tenancy: `officeId` on `Doctor` entity · all queue queries, WebSocket topics (`/topic/reception/overview/{officeId}`), and broadcasts scoped per office | ✅ |
| 6 | Idempotent check-in: `SETNX`-backed `tryAcquire()` (60 s TTL) · `idempotencyKey` field in `CheckInRequest` · duplicate returns `409 CONFLICT` | ✅ |
| 7 | Assistance boost: `requiresAssistance` boolean on `Token` · `PriorityEngine` subtracts 50 M from score → guaranteed near-front position | ✅ |

### Phase 3 — Patient Experience
| # | Feature | Status |
|---|---|---|
| 8 | Magic Link patient tracking: `GET /api/patient/status/{tokenId}` (public) · React `/status/:tokenId` page · 15 s auto-poll · `localStorage` persistence | ✅ |
| 9 | QR code modal in Reception UI: `QRModal.jsx` using `qrcode.react` · auto-opens after check-in · print/reprint · copy link · uses `window.location.origin` | ✅ |
| 10 | Telegram Magic Link: token confirmation + `/status` replies include `📱 Track your queue live: {url}` · env-var `FRONTEND_BASE_URL` | ✅ |

### Phase 4 — Scheduler & Tests
| # | Feature | Status |
|---|---|---|
| 11 | Starvation-prevention scheduler: `applyStarvationBoosts()` every 5 min · distributed leader lock · `starvationBoostCounter` Micrometer metric · officeId-scoped rebroadcast | ✅ |
| 12 | Test suite: 76 unit tests passing · `ClinicScheduledJobsTest` updated for new constructor · `IdempotencyServiceTest` covers `tryAcquire` | ✅ |

---

## Architecture Overview

```
Browser/Telegram
     │
     ▼
Spring Boot API (JWT-secured)
     ├── ReceptionController   ← check-in, appointment, reassign, no-show
     ├── DoctorController      ← call-next, consult start/complete/extend
     ├── PatientController     ← public /status/{tokenId} (Magic Link)
     ├── AdminController       ← analytics, audit log, staff management
     └── TelegramWebhookController ← guided intake, /status, /cancel
          │
          ▼
     QueueService / DoctorQueueService / PriorityEngine
          │              │
          ▼              ▼
      PostgreSQL       Redis ZSet (queue:doctor:{id}, score = priority)
                         │
                         ▼
              WebSocketBroadcastService
              → Redis Pub/Sub → RedisPubSubRelay → STOMP /topic/…/{officeId}
                         │
                    ClinicScheduledJobs
                    ├── autoExpireCalledTokens()   every 60 s
                    ├── applyStarvationBoosts()    every 5 min
                    └── recordQueueWaitTimes()     every 60 s
```

### Multi-Tenancy Model
- Every `Token` and `Doctor` carries `officeId`
- All queue queries, Redis keys, and WebSocket topics are scoped by `officeId`
- Reception overview topic: `/topic/reception/overview/{officeId}`
- Doctor queue topic: `/topic/doctor/{doctorId}`

### Priority Scoring (ascending ZSet — lower = served first)
```
score = createdAt_millis
      - (weightedPriority × 10_000)
      + (queueSize × 30_000)
      - [50_000_000 if requiresAssistance]     ← guaranteed near-front
```
Weights: severity (×50), age (×10), wait time (×5), service type (×20), appointment timing.

---

## Repository Structure
```
.
├── backend/                        Spring Boot API
│   └── src/main/java/…/
│       ├── controller/             REST + Telegram + Patient (public)
│       ├── service/                QueueService, PriorityEngine, ClinicScheduledJobs
│       ├── websocket/              RedisPubSubRelay, WebSocketBroadcastService
│       ├── idempotency/            IdempotencyService (SETNX guard)
│       └── db/migration/           V1–V8 Flyway migrations
├── smartqueue-frontend/            React SPA
│   └── src/
│       ├── pages/                  DoctorPanel, ReceptionPanel, PatientStatusPage
│       ├── components/             QRModal, TokenCard, ChatWindow
│       └── websocket/socket.js
├── infra/                          Prometheus + Grafana provisioning
└── docker-compose.yml
```

---

## Environment Variables (key)
| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | — | PostgreSQL JDBC URL |
| `REDIS_URL` | `redis://127.0.0.1:6379` | Redis connection |
| `JWT_SECRET` | — | HS256 signing key |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed CORS origins |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | Magic Link base URL (Telegram + QR) |
| `CLINIC_OPEN_HOUR` | `0` | Walk-in registration open hour |
| `CLINIC_CLOSE_HOUR` | `23` | Walk-in registration close hour |
| `AI_ENABLED` | `false` | Enable Ollama AI triage |
| `TELEGRAM_BOT_TOKEN` | — | Telegram bot API token |

---

## Quick Start (Docker Compose)
1. `cp .env.example .env` — fill in `JWT_SECRET` at minimum
2. `docker compose up -d`
3. Open:
   - Frontend: `http://localhost:3000`
   - Backend health: `http://localhost:8080/actuator/health`
   - Prometheus: `http://localhost:9090`
   - Grafana: `http://localhost:3001` (admin / admin)

## Local Development (no Docker)
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Frontend
cd smartqueue-frontend && npm install && npm start

# Tests (unit only, no DB required)
cd backend && ./mvnw test -Dspring.test.context.cache.maxSize=0
```

## Common Ports
| Port | Service |
|---|---|
| 5432 | PostgreSQL |
| 6379 | Redis |
| 8080 | Backend API |
| 3000 | Frontend |
| 9090 | Prometheus |
| 3001 | Grafana |
| 11434 | Ollama |

## Detailed Docs
- `docs/USER_MANUAL.md`
- `docs/DEPLOYMENT_INSTRUCTIONS.md`
- `docs/CODE_DIRECTORY_STRUCTURE.md`
- `docs/DATABASE_SCRIPTS.md`
- `docs/API_REFERENCE.md`
- `docs/PROJECT_SUBMISSION_INDEX.md`
