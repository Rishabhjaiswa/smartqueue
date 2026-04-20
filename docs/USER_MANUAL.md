# User Manual

SmartQueue supports multiple roles and interfaces:
- **Receptionist UI** (web): patient check-in, appointment creation, token management
- **Doctor UI** (web): call next, start/complete consultation, extend consultation time
- **Admin UI** (web): staff management, analytics, history, audit logs
- **Display Board** (web): public view for showing called/next tokens
- **Telegram Bot** (chat): guided token booking + status/queue commands

## 1) Web Application Access

### Login
1. Open the frontend: `http://localhost:3000`
2. Login page: `http://localhost:3000/login`
3. After login, navigate to your role page:
   - Doctor: `http://localhost:3000/doctor`
   - Receptionist: `http://localhost:3000/reception`
   - Admin: `http://localhost:3000/admin`

### Display Board (No Login)
- `http://localhost:3000/display` (or `/board`)

## 2) Receptionist Workflow (Web)

Typical front-desk flow:
1. **Check-in** a walk-in patient (creates/links patient and issues a token).
2. Create an **appointment** (if applicable) to reserve time.
3. Monitor **active tokens** and handle **no-shows**.
4. Reassign tokens to a different doctor if needed (when supported by the UI).

## 3) Doctor Workflow (Web)

Typical consultation flow:
1. Open Doctor panel: `/doctor`
2. Use **Call Next** to call the next patient/token in the queue.
3. Mark a token **In Consultation** when the patient arrives.
4. **Complete** the token when consultation ends.
5. **Extend** consultation time if needed (for more accurate ETAs).

## 4) Admin Workflow (Web)

Admin features include:
1. **Create staff users** (doctor/reception/admin depending on your policies).
2. View **staff list**.
3. Review **analytics** (throughput, durations, completion metrics).
4. View **history** and **audit logs** for operational traceability.

## 5) Telegram Bot (Patient Booking)

### Commands
The bot supports:
- `/start` — begin guided booking flow
- `/patients` — select the active patient profile in this chat (or create a new one)
- `/queue` — see your queue info
- `/status` — see your active tokens (WAITING/CALLED/IN_CONSULTATION)
- `/cancel` — cancel your latest active token (if cancellable)
- `/help` — show help

### Guided Booking Flow (Typical)
1. Type `/start`
2. If you have existing patient profiles, select one (or reply `new`).
3. Enter patient name and age (if creating new).
4. Describe symptoms/reason for visit.
5. The system confirms the generated token (doctor, position, estimated wait).

### Live Updates
When a doctor calls your token, the backend sends Telegram notifications to the chat (when the patient profile is linked to that chat).

## 6) Troubleshooting (User-Facing)
- If Telegram says “session expired”, run `/start` again.
- If you see “You already have an active token”, use `/status` or `/cancel`.
- If the web UI shows “Unauthorized”, login again or confirm you are using the correct role page.

