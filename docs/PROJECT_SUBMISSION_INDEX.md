# Project Package Contents (No Zip)

This repository already contains the **actual code**, **static assets**, and **database scripts**. This document is an index that maps each submission requirement to the corresponding files/folders.

## Full Documentation
- Overview + quickstart: `README.md`
- Detailed manuals: `docs/`

## User Manual
- `docs/USER_MANUAL.md`

## Deployment Instructions
- `docs/DEPLOYMENT_INSTRUCTIONS.md`
- Docker stack definition: `docker-compose.yml`
- Example environment file: `.env.example`

## Code Directory Structure + Actual Code
- Structure overview: `docs/CODE_DIRECTORY_STRUCTURE.md`
- Backend code: `backend/`
- Frontend code + static files: `smartqueue-frontend/` (includes `public/` and `src/`)
- Infra configs: `infra/`

## Database Scripts
- Flyway migrations: `backend/src/main/resources/db/migration/`
- Documentation: `docs/DATABASE_SCRIPTS.md`

## Other Source Code / Static Files
- Frontend nginx config: `smartqueue-frontend/nginx.conf`
- Backend Dockerfile: `backend/Dockerfile`
- Frontend Dockerfile: `smartqueue-frontend/Dockerfile`
- Monitoring configs: `infra/prometheus/prometheus.yml`, `infra/grafana/provisioning/datasources/prometheus.yml`

## What to Exclude If You Later Create a Zip
If you later need a clean submission zip, exclude build artifacts and dependency folders:
- `backend/target/`
- `smartqueue-frontend/node_modules/`
- `smartqueue-frontend/build/` (optional; include only if you need pre-built static output)
- `.env` (secrets)

