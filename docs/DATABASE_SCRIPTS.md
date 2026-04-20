# Database Scripts (PostgreSQL + Flyway)

SmartQueue uses **Flyway** for schema migrations. The backend is configured with:
- `spring.jpa.hibernate.ddl-auto=none` (no auto-DDL)
- `spring.flyway.enabled=true`
- migrations location: `backend/src/main/resources/db/migration/`

## Migration Files
Located under `backend/src/main/resources/db/migration/`:
- `V1__init.sql` — initial schema
- `V2__clinic_patients.sql` — clinic/patient structures
- `V3__alter_tokens_clinic.sql` — token table changes related to clinic
- `V4__add_appointment_time.sql` — appointment scheduling fields
- `V5__add_audit_logs.sql` — audit logging tables
- `V6__add_performance_indexes.sql` — indexes for performance

## How Migrations Run
- On backend startup, Flyway applies any pending migrations to the configured database.
- Configuration is driven by environment variables in `backend/src/main/resources/application.properties`:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## Local (Docker Compose) Database
`docker-compose.yml` starts PostgreSQL as `smartqueue-postgres` and the backend automatically runs Flyway on boot.

## Production Notes
- Always back up the database before deploying a version with new migrations.
- Do not edit already-applied migration files; create a new versioned migration instead.

