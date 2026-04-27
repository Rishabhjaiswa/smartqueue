#!/bin/bash
# -----------------------------------------------------------------------------
# Script to run the backend locally without conflicting with the background Docker containers.
# It uses port 8081 and connects to the dev database (5433) and dev redis (6380).
# -----------------------------------------------------------------------------

# Navigate to backend directory
cd backend

# Load .env from parent directory if it exists to match Docker Compose DB credentials
if [ -f ../.env ]; then
  # Read .env line by line, ignoring comments and empty lines
  while IFS='=' read -r key value; do
    if [[ ! $key =~ ^# && -n $key ]]; then
      # Strip surrounding quotes from value if present, and export
      value="${value%\"}"
      value="${value#\"}"
      export "$key=$value"
    fi
  done < ../.env
fi

export PORT=8081
export DB_URL=jdbc:postgresql://localhost:5433/smartqueue_dev
export DB_USERNAME=${DB_USERNAME:-smartqueue_dev}
export DB_PASSWORD=${DB_PASSWORD:-smartqueue_dev}
export REDIS_URL=redis://localhost:6380
export CORS_ORIGINS=http://localhost:3001
export JWT_SECRET=${JWT_SECRET:-my_super_secret_jwt_token_for_local_dev_12345}

echo "====================================================="
echo "Starting Smartqueue Backend (Dev Environment)"
echo "Backend running on: http://localhost:8081"
echo "Connected to Postgres on 5433 | Redis on 6380"
echo "====================================================="

# Run Spring Boot
./mvnw spring-boot:run
