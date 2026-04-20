# API Reference (High-Level)

This is a high-level reference to help testers and integrators. For exact request/response DTOs, see the controller classes under `backend/src/main/java/com/smartqueue/backend/controller/`.

## Auth
Base path: `/api/auth`
- `POST /api/auth/login` — authenticate user and obtain JWT
- `GET /api/auth/me` — returns current user info (requires JWT)

## Queue / Tokens
Base path: `/api`
- `POST /api/token` — create token (patient check-in / booking)
- `GET /api/queue/{officeId}` — queue state for an office
- `POST /api/staff/next` — staff action: fetch next token
- `POST /api/staff/complete/{tokenId}` — staff completes a token
- `POST /api/staff/noshow/{tokenId}` — mark token as no-show
- `POST /api/staff/override` — override/re-prioritize (when enabled)

## Reception
Base path: `/api/reception`
- `POST /api/reception/checkin`
- `POST /api/reception/appointment`
- `GET /api/reception/overview`
- `POST /api/reception/token/{tokenId}/noshow`
- `PUT /api/reception/token/{tokenId}/doctor/{doctorId}`
- `POST /api/reception/token/{tokenId}/reinstate`
- `GET /api/reception/doctors`
- `GET /api/reception/tokens/waiting`
- `GET /api/reception/tokens/active`
- `GET /api/reception/tokens/reinstatable`
- `GET /api/reception/display`

## Doctor
Base path: `/api/doctor`
- `POST /api/doctor/call-next`
- `POST /api/doctor/token/{tokenId}/in-consultation`
- `POST /api/doctor/token/{tokenId}/complete`
- `POST /api/doctor/token/{tokenId}/extend`
- `PUT /api/doctor/availability`
- `GET /api/doctor/queue`

## Admin
Base path: `/api/admin`
- `POST /api/admin/create-staff`
- `GET /api/admin/staff`
- `GET /api/admin/analytics`
- `GET /api/admin/history`
- `GET /api/admin/audit-logs`
- `POST /api/admin/staff/{staffUserId}/reset-password`

## Telegram Webhook
- `GET /telegram/webhook` — health/test endpoint
- `POST /telegram/webhook` — Telegram update receiver

## Actuator / Metrics
- `GET /actuator/health`
- `GET /actuator/prometheus`

