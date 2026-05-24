# 🏥 SmartQueue Clinic Management System

**SmartQueue** is a comprehensive, AI-powered clinic queue management system designed to streamline patient flow, optimize doctor utilization, and provide real-time updates to patients via Telegram. It bridges the gap between walk-in appointments, receptionist operations, and doctor consultations using a robust real-time architecture.

## ✨ Key Features
- **Real-Time Token Tracking:** WebSockets broadcast live queue updates to a public Display Board.
- **AI-Guided Patient Booking:** Patients can interact with a Telegram bot to book tokens, describe symptoms, and get an AI-estimated wait time.
- **Role-Based Web UI:** Dedicated portals for **Doctors** (call next, extend time, complete), **Receptionists** (check-in, override, manual booking), and **Admins** (staff management, analytics).
- **Intelligent Queue Rebalancing:** Automatically prevents starvation of delayed tokens.
- **Full Observability:** Integrated Prometheus and Grafana for system health and operational analytics.

## 🛠️ Technology Stack
- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Spring AI, WebSocket
- **Frontend:** React (Create React App), TailwindCSS (served via Nginx)
- **Databases:** PostgreSQL (Relational Data & Flyway Migrations), Redis (Queue State & Caching)
- **AI & Bot:** Ollama (Llama 3 hosted locally), Telegram Bot API
- **Infrastructure:** Docker, Docker Compose, Prometheus, Grafana

---

## 🚀 Live Deployed Version

You can access the live, deployed version of the project here:

### 🌐 Web Portals
| Portal | Live Link | Demo Credentials |
|--------|-----------|------------------|
| **Admin Portal** | [https://smartqueue-frontend-theta.vercel.app/admin](https://smartqueue-frontend-theta.vercel.app/admin) | User: `admin` <br> Pass: `admin@123` |
| **Doctor Portal** | [https://smartqueue-frontend-theta.vercel.app/doctor](https://smartqueue-frontend-theta.vercel.app/doctor) | User: `drsharm` <br> Pass: `sharma@123` |
| **Reception Portal** | [https://smartqueue-frontend-theta.vercel.app/reception](https://smartqueue-frontend-theta.vercel.app/reception) | User: `reception` <br> Pass: `reception@123` |
| **Display Board** | [https://smartqueue-frontend-theta.vercel.app/display](https://smartqueue-frontend-theta.vercel.app/display) | *No login required* |

### 📱 Patient Bot
| Service | Link | Description |
|---------|------|-------------|
| **Telegram Bot** | [https://t.me/Smartqueue26_bot](https://t.me/Smartqueue26_bot) | Interact with the AI bot to book tokens, describe symptoms, and get live queue updates. Send `/start` to begin. |

---

## 💻 Local Access Links (Docker Compose)

If you are running the project locally, you can access the various services using the links below:

### 🌐 Web Interfaces
| Service | Link / URL | Default Credentials | Description |
|---------|-----------|---------------------|-------------|
| **Frontend Portal** | [http://localhost:3000](http://localhost:3000) | *(Use demo credentials above)* | Main dashboard. After logging in, navigate to `/doctor`, `/reception`, or `/admin`. |
| **Display Board** | [http://localhost:3000/display](http://localhost:3000/display) | *No login required* | Public display board showing called/waiting tokens. |
| **Grafana** | [http://localhost:3001](http://localhost:3001) | User: `admin` <br> Pass: `admin` | View operational analytics, queue lengths, and system metrics. |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | *No login required* | Raw metrics scraping endpoint. |
| **Backend API** | [http://localhost:8080](http://localhost:8080) | *JWT Authenticated* | REST API and WebSocket (`ws://localhost:8080`) base URL. |

### 🗄️ Backend Infrastructure
| Service | Address | Default Credentials | Description |
|---------|---------|---------------------|-------------|
| **PostgreSQL** | `localhost:5432` | User: `smartqueue` <br> Pass: `smartqueue` | Relational database (schema managed by Flyway). |
| **Redis** | `localhost:6379` | *No password* | In-memory datastore for active queues. |
| **Ollama (AI)** | `localhost:11434` | *N/A* | Local LLM host for patient symptom parsing. |

---

## 📖 Operating & Deployment Guide

The entire system is containerized and can be launched using Docker Compose.

### 1. Environment Configuration
1. Clone the repository.
2. In the root directory, create your `.env` file:
   ```bash
   cp .env.example .env
   ```
3. Open `.env` and configure essential variables:
   - `JWT_SECRET`: Generate a secure 32+ character string.
   - `TELEGRAM_BOT_TOKEN`: Your Telegram bot token (from BotFather).
   - `TELEGRAM_BOT_WEBHOOK_BASE_URL`: Public HTTPS URL (e.g., via Ngrok) pointing to your backend so Telegram can send webhook events.

### 2. Start the Infrastructure
Start all services in the background:
```bash
docker compose up -d
```

### 3. Initialize the AI Model (Critical First Step)
For the Telegram Bot to parse symptoms, the Ollama container requires the Llama 3 model. On the very first run, execute:
```bash
docker exec smartqueue-ollama ollama pull llama3
```

### 4. System Shutdown & Reset
To stop the application:
```bash
docker compose down
```
To stop the application and **completely wipe all data** (database, redis, and downloaded AI models):
```bash
docker compose down -v
```
---

## ⚙️ Advanced Configuration & System Capabilities

The SmartQueue backend is designed for production readiness and can be heavily customized via environment variables (or in `application.properties`).

### 🔧 High-Availability & Concurrency
- **Circuit Breakers (Resilience4j):** Wraps the local Ollama AI calls to prevent cascading system failures if the LLM becomes unresponsive.
- **Distributed Locking (Redisson):** Ensures atomic operations on queue state, preventing race conditions when Receptionists and Doctors mutate the queue simultaneously.
- **AI Triage Thread Pool:** The AI token generation runs asynchronously. You can tune concurrency for high-traffic clinics via `AI_EXECUTOR_CORE`, `AI_EXECUTOR_MAX`, and `AI_EXECUTOR_QUEUE`.

### 🎛️ Clinical Parameters Tuning
You can inject the following variables via `.env` or Docker to modify clinic behavior without recompiling:

| Variable / Property | Default | Description |
|---------------------|---------|-------------|
| `CLINIC_OPEN_HOUR` / `CLINIC_CLOSE_HOUR` | `0` / `23` | Hours of operation for the clinic. |
| `CLINIC_BREAK_START_HOUR` / `END_HOUR` | `13` / `14` | Doctor break times (halts active queue calls). |
| `ENABLE_AUTO_REBALANCE` | `true` | Dynamically redistributes patients if one doctor's queue becomes heavily backed up. |
| `STARVATION_SCORE_REDUCTION` | `500000` | The priority score penalty applied to delayed tokens to ensure no patient waits indefinitely. |
| `TELEGRAM_SESSION_TTL_MINUTES` | `180` | How long a patient's conversational state remains active in the Telegram bot before resetting. |

---

## 🧪 Testing

To run the automated tests for both the backend and the frontend:

**Backend (Java/Spring Boot):**
```bash
cd backend
./mvnw test
```

**Frontend (React):**
```bash
cd smartqueue-frontend
npm test
```

---

## 📚 Further Reading & Documentation
Detailed architectural and operational documentation can be found in the `docs/` folder:
- [User Manual](docs/USER_MANUAL.md) - Detailed workflows for Doctors, Receptionists, and Admins.
- [Deployment Instructions](docs/DEPLOYMENT_INSTRUCTIONS.md) - Cloud/Production deployment notes.
- [Code & Directory Structure](docs/CODE_DIRECTORY_STRUCTURE.md) - Architecture and codebase navigation.
- [Database Scripts](docs/DATABASE_SCRIPTS.md) - Entity definitions and Flyway scripts.
- [API Reference](docs/API_REFERENCE.md) - REST API endpoints and WebSocket channels.
